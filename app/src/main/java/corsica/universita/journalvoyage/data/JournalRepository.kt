package corsica.universita.journalvoyage.data

import android.util.Log
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import corsica.universita.journalvoyage.data.local.EtapeEntity
import corsica.universita.journalvoyage.data.local.EtapeWithPhotos
import corsica.universita.journalvoyage.data.local.JournalDatabase
import corsica.universita.journalvoyage.data.local.PhotoEntity
import corsica.universita.journalvoyage.data.local.TrackPointEntity
import corsica.universita.journalvoyage.data.local.TrajetEntity
import corsica.universita.journalvoyage.data.local.TrajetWithCount
import corsica.universita.journalvoyage.data.local.VoyageEntity
import corsica.universita.journalvoyage.data.remote.EtapeDto
import corsica.universita.journalvoyage.data.remote.SyncApi
import corsica.universita.journalvoyage.tracking.TrackPoint
import corsica.universita.journalvoyage.work.SyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/**
 * La source de vérité : le seul endroit qui décide d'où viennent les données et où elles vont.
 * Les écrans, le SyncWorker et le service FCM passent tous par lui ; les ViewModels ne connaissent ni Room ni HTTP.
 *
 * Offline first, comme la PWA : une étape est d'abord écrite dans Room (instantané, hors ligne compris),
 * puis l'envoi est confié à WorkManager, qui le fera quand l'OS le permettra, app fermée ou tuée.
 * La PWA tentait d'abord un fetch direct, puis l'outbox en cas d'échec ; ici, il n'y a qu'un chemin.
 */
class JournalRepository(
    private val db: JournalDatabase,
    private val api: SyncApi,
    private val workManager: WorkManager,
    private val photoStore: PhotoStore,
) {
    private val voyageDao = db.voyageDao()
    private val etapeDao = db.etapeDao()

    // ---- Voyages ----

    val voyages: Flow<List<Voyage>> = voyageDao.all().map { list ->
        list.map { Voyage(it.voyage.id, it.voyage.titre, it.voyage.description, it.voyage.coverPath, it.voyage.createdAt, it.etapesCount) }
    }

    fun observeVoyage(id: Long): Flow<Voyage?> = voyageDao.observe(id).map { it?.toVoyage() }

    suspend fun getVoyage(id: Long): Voyage? = voyageDao.get(id)?.toVoyage()

    suspend fun latestVoyageId(): Long? = voyageDao.latestId()

    /** id = 0 : nouveau voyage ; sinon mise à jour. L'ancienne photo vitrine remplacée est effacée du disque. */
    suspend fun saveVoyage(id: Long, titre: String, description: String, coverPath: String?): Long {
        val old = if (id != 0L) voyageDao.get(id) else null
        val saved = voyageDao.upsert(
            VoyageEntity(
                id = id, titre = titre, description = description, coverPath = coverPath,
                createdAt = old?.createdAt ?: System.currentTimeMillis(),
            ),
        )
        if (old?.coverPath != null && old.coverPath != coverPath) photoStore.delete(listOf(old.coverPath))
        // @Upsert rend -1 quand la ligne existait déjà (UPDATE) : l'id est alors celui qu'on a passé.
        return if (id != 0L) id else saved
    }

    /** CASCADE efface les lignes des étapes et des photos ; les fichiers, c'est à nous. */
    suspend fun deleteVoyage(id: Long) {
        val voyage = voyageDao.get(id) ?: return
        val files = etapeDao.photoPathsOfVoyage(id) + listOfNotNull(voyage.coverPath)
        voyageDao.delete(voyage)
        photoStore.delete(files)
    }

    // ---- Étapes ----

    fun etapes(voyageId: Long): Flow<List<Etape>> = etapeDao.forVoyage(voyageId).map { list -> list.map { it.toEtape() } }

    fun observeEtape(id: Long): Flow<Etape?> = etapeDao.observe(id).map { it?.toEtape() }

    /** L'étape et ses photos dans UNE transaction : jamais une étape sans ses photos, même si l'app meurt entre les deux. */
    suspend fun addEtape(voyageId: Long, titre: String, description: String, lat: Double?, lng: Double?, photos: List<String>): Long {
        val id = db.withTransaction {
            val id = etapeDao.upsert(
                EtapeEntity(voyageId = voyageId, titre = titre, description = description, date = System.currentTimeMillis(), lat = lat, lng = lng),
            )
            etapeDao.insertPhotos(photos.map { PhotoEntity(etapeId = id, path = it) })
            id
        }
        enqueueSync()
        return id
    }

    suspend fun deleteEtape(id: Long) {
        val etape = etapeDao.get(id) ?: return
        val files = etapeDao.photoPaths(id)
        etapeDao.delete(etape)
        photoStore.delete(files)
    }

    // ---- v2 : les trajets (écrits par TrackingService, lus par les écrans) ----

    private val trajetDao = db.trajetDao()

    fun trajets(voyageId: Long): Flow<List<Trajet>> = trajetDao.forVoyage(voyageId).map { list -> list.map { it.toTrajet() } }

    fun observeTrajet(id: Long): Flow<Trajet?> = trajetDao.observe(id).map { it?.toTrajet() }

    fun trackPoints(trajetId: Long): Flow<List<TrackPoint>> = trajetDao.points(trajetId).map { list -> list.map { it.toPoint() } }

    suspend fun startTrajet(voyageId: Long): Long =
        trajetDao.insert(TrajetEntity(voyageId = voyageId, startedAt = System.currentTimeMillis()))

    /** Le point et la nouvelle distance ensemble : jamais un point compté sans être en base, ou l'inverse. */
    suspend fun addTrackPoint(trajetId: Long, p: TrackPoint, distanceM: Double) = db.withTransaction {
        trajetDao.insertPoint(TrackPointEntity(trajetId = trajetId, lat = p.lat, lng = p.lng, altitude = p.altitude, accuracy = p.accuracy, time = p.time))
        trajetDao.setDistance(trajetId, distanceM)
    }

    suspend fun finishTrajet(id: Long) = trajetDao.finish(id, System.currentTimeMillis())

    suspend fun closeOrphanTrajets(exceptId: Long?): Int = trajetDao.closeOrphans(exceptId ?: -1)

    suspend fun deleteTrajet(id: Long) {
        trajetDao.get(id)?.let { trajetDao.delete(it) }
    }

    suspend fun trackPointsOnce(trajetId: Long): List<TrackPoint> = trajetDao.pointsOnce(trajetId).map { it.toPoint() }

    // ---- Premier lancement : les mêmes données de démonstration que la PWA ----

    /** initVoyages() + hydrateDestinations() de la PWA : un voyage et trois étapes, déjà « synchronisées ». */
    suspend fun seedIfEmpty() {
        if (voyageDao.count() > 0) return
        Log.d(TAG, "Premier lancement : voyage de démonstration")
        val cover = photoStore.fromAsset("seed/bavella.jpg")
        val seeds = listOf(
            Triple("Aiguilles de Bavella", "Randonnée spectaculaire à travers les aiguilles de granite rouge. Un panorama à couper le souffle sur les montagnes corses et la mer Tyrrhénienne.", "seed/bavella.jpg"),
            Triple("Calanques de Piana", "Classées au patrimoine mondial de l'UNESCO, ces formations rocheuses de granit rose plongent dans une mer turquoise. Un spectacle naturel unique en Méditerranée.", "seed/piana.jpg"),
            Triple("Plage de Palombaggia", "Sable fin, eaux cristallines et pins parasols : l'une des plus belles plages de France. Idéale pour une journée de détente entre deux randonnées.", "seed/palombaggia.jpg"),
        )
        val coords = listOf(41.7957 to 9.2259, 42.2386 to 8.6464, 41.5594 to 9.3247)
        val photos = seeds.map { photoStore.fromAsset(it.third) }
        db.withTransaction {
            val voyageId = voyageDao.upsert(
                VoyageEntity(
                    titre = "Échappée en Corse",
                    description = "Aventures et randonnées au cœur des paysages corses, entre mer et montagnes.",
                    coverPath = cover, createdAt = System.currentTimeMillis(),
                ),
            )
            val now = System.currentTimeMillis()
            seeds.forEachIndexed { i, (titre, desc, _) ->
                val id = etapeDao.upsert(
                    EtapeEntity(
                        voyageId = voyageId, titre = titre, description = desc, date = now - (2 - i) * 86_400_000L,
                        lat = coords[i].first, lng = coords[i].second, synced = true,
                    ),
                )
                etapeDao.insertPhotos(listOf(PhotoEntity(etapeId = id, path = photos[i])))
            }
        }
    }

    // ---- L'outbox ----

    /** Le badge « n en attente ». */
    val pendingCount: Flow<Int> = etapeDao.unsyncedCount()

    /** La demande à l'OS : « lance SyncWorker, une fois, dès qu'il y a du réseau ». L'OS choisit le moment. */
    fun enqueueSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS) // 30 s, 1 min, 2 min…
            .build()
        // Un seul travail « sync » : dix étapes hors ligne = une demande. KEEP garde celle qui attend déjà.
        workManager.enqueueUniqueWork("sync", ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Appelé par SyncWorker : POST de chaque étape synced = 0, puis synced = 1.
     * Une IOException (réseau) ou une HttpException (serveur) remonte au worker, qui décide.
     */
    suspend fun syncPending(): Int {
        val sent = mutableListOf<Long>()
        try {
            for (e in etapeDao.unsynced()) {
                val dto = with(e.etape) { EtapeDto(id, voyageId, titre, description, lat, lng, date, e.photos.size) }
                val echo = api.push(dto)
                Log.d(TAG, "Écho du serveur pour l'étape ${dto.id} : ${echo.json}")
                sent += dto.id
            }
        } finally {
            // Si un envoi échoue en cours de route, celles déjà parties sont marquées : sinon, renvoyées en double.
            if (sent.isNotEmpty()) etapeDao.markSynced(sent)
        }
        return sent.size
    }

    private companion object {
        const val TAG = "Journal"
    }
}

private fun TrajetWithCount.toTrajet() = with(trajet) { Trajet(id, voyageId, startedAt, endedAt, distanceM, pointsCount) }

private fun TrackPointEntity.toPoint() = TrackPoint(lat, lng, altitude, accuracy, time)

private fun VoyageEntity.toVoyage() = Voyage(id, titre, description, coverPath, createdAt)

private fun EtapeWithPhotos.toEtape() = with(etape) {
    Etape(id, voyageId, titre, description, date, lat, lng, synced, photos.map { it.path })
}

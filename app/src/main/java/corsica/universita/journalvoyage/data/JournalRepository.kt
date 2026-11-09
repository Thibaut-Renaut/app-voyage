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

/** Source de vérité unique : écritures dans Room d'abord, envoi au serveur confié à WorkManager. */
class JournalRepository(
    private val db: JournalDatabase,
    private val api: SyncApi,
    private val workManager: WorkManager,
    private val photoStore: PhotoStore,
) {
    private val voyageDao = db.voyageDao()
    private val etapeDao = db.etapeDao()

    val voyages: Flow<List<Voyage>> = voyageDao.all().map { list ->
        list.map { Voyage(it.voyage.id, it.voyage.titre, it.voyage.description, it.voyage.coverPath, it.voyage.createdAt, it.etapesCount) }
    }

    suspend fun getVoyage(id: Long): Voyage? = voyageDao.get(id)?.toVoyage()

    suspend fun saveVoyage(id: Long, titre: String, description: String, coverPath: String?): Long {
        val old = if (id != 0L) voyageDao.get(id) else null
        val saved = voyageDao.upsert(
            VoyageEntity(
                id = id, titre = titre, description = description, coverPath = coverPath,
                createdAt = old?.createdAt ?: System.currentTimeMillis(),
            ),
        )
        if (old?.coverPath != null && old.coverPath != coverPath) photoStore.delete(listOf(old.coverPath))
        // @Upsert renvoie -1 quand la ligne existait déjà.
        return if (id != 0L) id else saved
    }

    suspend fun deleteVoyage(id: Long) {
        val voyage = voyageDao.get(id) ?: return
        val files = etapeDao.photoPathsOfVoyage(id) + listOfNotNull(voyage.coverPath)
        voyageDao.delete(voyage)
        photoStore.delete(files)
    }

    fun etapes(voyageId: Long): Flow<List<Etape>> = etapeDao.forVoyage(voyageId).map { list -> list.map { it.toEtape() } }

    fun observeEtape(id: Long): Flow<Etape?> = etapeDao.observe(id).map { it?.toEtape() }

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

    private val trajetDao = db.trajetDao()

    fun trajets(voyageId: Long): Flow<List<Trajet>> = trajetDao.forVoyage(voyageId).map { list -> list.map { it.toTrajet() } }

    fun observeTrajet(id: Long): Flow<Trajet?> = trajetDao.observe(id).map { it?.toTrajet() }

    fun trackPoints(trajetId: Long): Flow<List<TrackPoint>> = trajetDao.points(trajetId).map { list -> list.map { it.toPoint() } }

    suspend fun startTrajet(voyageId: Long): Long =
        trajetDao.insert(TrajetEntity(voyageId = voyageId, startedAt = System.currentTimeMillis()))

    suspend fun addTrackPoint(trajetId: Long, p: TrackPoint, distanceM: Double) = db.withTransaction {
        trajetDao.insertPoint(TrackPointEntity(trajetId = trajetId, lat = p.lat, lng = p.lng, altitude = p.altitude, accuracy = p.accuracy, time = p.time))
        trajetDao.setDistance(trajetId, distanceM)
    }

    suspend fun finishTrajet(id: Long) = trajetDao.finish(id, System.currentTimeMillis())

    suspend fun renameTrajet(id: Long, nom: String) = trajetDao.rename(id, nom.trim().ifEmpty { null })

    suspend fun closeOrphanTrajets(exceptId: Long?): Int = trajetDao.closeOrphans(exceptId ?: -1)

    suspend fun deleteTrajet(id: Long) {
        trajetDao.get(id)?.let { trajetDao.delete(it) }
    }

    suspend fun trackPointsOnce(trajetId: Long): List<TrackPoint> = trajetDao.pointsOnce(trajetId).map { it.toPoint() }

    suspend fun seedIfEmpty() {
        if (voyageDao.count() > 0) return
        Log.d(TAG, "Premier lancement : voyage de démonstration")
        val cover = photoStore.fromAsset("seed/bavella.jpg")
        val now = System.currentTimeMillis()
        db.withTransaction {
            val voyageId = voyageDao.upsert(
                VoyageEntity(
                    titre = "Échappée en Corse",
                    description = "Aventures et randonnées au cœur des paysages corses, entre mer et montagnes.",
                    coverPath = cover, createdAt = now,
                ),
            )
            Destinations.all.forEachIndexed { i, d ->
                etapeDao.upsert(EtapeEntity(voyageId = voyageId, titre = d.etapeTitre, description = d.description, date = now - i, synced = true))
            }
        }
    }

    suspend fun voyageCount(): Int = voyageDao.count()

    val pendingCount: Flow<Int> = etapeDao.unsyncedCount()

    fun enqueueSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork("sync", ExistingWorkPolicy.KEEP, request)
    }

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
            // Les étapes déjà envoyées sont marquées même si une suivante échoue : pas de doublon au prochain essai.
            if (sent.isNotEmpty()) etapeDao.markSynced(sent)
        }
        return sent.size
    }

    private companion object {
        const val TAG = "Journal"
    }
}

private fun TrajetWithCount.toTrajet() = with(trajet) { Trajet(id, voyageId, startedAt, endedAt, distanceM, pointsCount, nom) }

private fun TrackPointEntity.toPoint() = TrackPoint(lat, lng, altitude, accuracy, time)

private fun VoyageEntity.toVoyage() = Voyage(id, titre, description, coverPath, createdAt)

private fun EtapeWithPhotos.toEtape() = with(etape) {
    Etape(id, voyageId, titre, description, date, lat, lng, synced, photos.map { it.path })
}

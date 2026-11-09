package corsica.universita.journalvoyage.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Les accès aux tables : le SQL, vérifié à la compilation par Room.
 * Lire : un Flow, réémis à chaque écriture dans la table. Écrire : suspend, hors du main thread.
 */
@Dao
interface VoyageDao {
    @Query(
        "SELECT v.*, (SELECT COUNT(*) FROM etapes e WHERE e.voyageId = v.id) AS etapesCount " +
            "FROM voyages v ORDER BY v.createdAt, v.id", // l'ordre de création, comme db.getAll("voyages") de la PWA
    )
    fun all(): Flow<List<VoyageWithCount>>

    @Query("SELECT * FROM voyages WHERE id = :id")
    suspend fun get(id: Long): VoyageEntity?

    @Query("SELECT COUNT(*) FROM voyages")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(v: VoyageEntity): Long

    @Delete
    suspend fun delete(v: VoyageEntity)
}

@Dao
interface EtapeDao {
    @Transaction
    @Query("SELECT * FROM etapes WHERE voyageId = :voyageId ORDER BY date DESC")
    fun forVoyage(voyageId: Long): Flow<List<EtapeWithPhotos>>

    @Transaction
    @Query("SELECT * FROM etapes WHERE id = :id")
    fun observe(id: Long): Flow<EtapeWithPhotos?>

    @Query("SELECT * FROM etapes WHERE id = :id")
    suspend fun get(id: Long): EtapeEntity?

    @Upsert
    suspend fun upsert(e: EtapeEntity): Long

    @Delete
    suspend fun delete(e: EtapeEntity)

    @Insert
    suspend fun insertPhotos(photos: List<PhotoEntity>)

    /** Les fichiers à effacer avant que CASCADE n'efface les lignes qui les désignent. */
    @Query("SELECT path FROM photos WHERE etapeId = :etapeId")
    suspend fun photoPaths(etapeId: Long): List<String>

    @Query("SELECT p.path FROM photos p JOIN etapes e ON p.etapeId = e.id WHERE e.voyageId = :voyageId")
    suspend fun photoPathsOfVoyage(voyageId: Long): List<String>

    // L'outbox, en SQL : ce que la PWA gardait dans l'object store « outbox ».
    @Transaction
    @Query("SELECT * FROM etapes WHERE synced = 0 ORDER BY date")
    suspend fun unsynced(): List<EtapeWithPhotos>

    @Query("UPDATE etapes SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    /** Le badge « n en attente » : il vient de Room, pas de WorkManager. */
    @Query("SELECT COUNT(*) FROM etapes WHERE synced = 0")
    fun unsyncedCount(): Flow<Int>
}

/** v2 : les trajets et leurs points. */
@Dao
interface TrajetDao {
    @Query(
        "SELECT t.*, (SELECT COUNT(*) FROM track_points p WHERE p.trajetId = t.id) AS pointsCount " +
            "FROM trajets t WHERE t.voyageId = :voyageId ORDER BY t.startedAt DESC",
    )
    fun forVoyage(voyageId: Long): Flow<List<TrajetWithCount>>

    @Query(
        "SELECT t.*, (SELECT COUNT(*) FROM track_points p WHERE p.trajetId = t.id) AS pointsCount " +
            "FROM trajets t WHERE t.id = :id",
    )
    fun observe(id: Long): Flow<TrajetWithCount?>

    @Query("SELECT * FROM trajets WHERE id = :id")
    suspend fun get(id: Long): TrajetEntity?

    @Insert
    suspend fun insert(t: TrajetEntity): Long

    @Insert
    suspend fun insertPoint(p: TrackPointEntity)

    @Query("UPDATE trajets SET distanceM = :distanceM WHERE id = :id")
    suspend fun setDistance(id: Long, distanceM: Double)

    @Query("UPDATE trajets SET nom = :nom WHERE id = :id")
    suspend fun rename(id: Long, nom: String?)

    @Query("UPDATE trajets SET endedAt = :endedAt WHERE id = :id")
    suspend fun finish(id: Long, endedAt: Long)

    /** Les points dans l'ordre : le tracé, réémis à chaque nouveau point (l'écran se dessine en direct). */
    @Query("SELECT * FROM track_points WHERE trajetId = :trajetId ORDER BY time")
    fun points(trajetId: Long): Flow<List<TrackPointEntity>>

    @Query("SELECT * FROM track_points WHERE trajetId = :trajetId ORDER BY time")
    suspend fun pointsOnce(trajetId: Long): List<TrackPointEntity>

    /**
     * Un trajet resté « en cours » alors qu'aucun service ne l'enregistre (process tué par l'OS) :
     * il est clos à l'heure de son dernier point.
     */
    @Query(
        "UPDATE trajets SET endedAt = COALESCE((SELECT MAX(time) FROM track_points WHERE trajetId = trajets.id), startedAt) " +
            "WHERE endedAt IS NULL AND id != :exceptId",
    )
    suspend fun closeOrphans(exceptId: Long): Int

    @Delete
    suspend fun delete(t: TrajetEntity)
}

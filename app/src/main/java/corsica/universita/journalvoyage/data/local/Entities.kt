package corsica.universita.journalvoyage.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/*
 * Les tables de la base, l'équivalent des object stores IndexedDB de la PWA :
 *   PWA « voyages »  → table voyages
 *   PWA « etapes »   → table etapes (+ table photos : un tableau dans IndexedDB, des lignes en SQL)
 *   PWA « outbox »   → pas de table : la colonne etapes.synced (0 = à envoyer), lue par le SyncWorker
 * Toute modification d'une @Entity impose de monter la version de JournalDatabase ET d'écrire la migration.
 */

@Entity(tableName = "voyages")
data class VoyageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titre: String,
    val description: String,
    /** La photo vitrine : un chemin dans filesDir/photos/, jamais les octets en base. NULL = la boussole. */
    val coverPath: String? = null,
    val createdAt: Long,
)

/** CASCADE : supprimer un voyage supprime ses étapes (et, en chaîne, leurs photos) dans la même transaction. */
@Entity(
    tableName = "etapes",
    foreignKeys = [ForeignKey(VoyageEntity::class, ["id"], ["voyageId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("voyageId")],
)
data class EtapeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val voyageId: Long,
    val titre: String,
    val description: String,
    val date: Long,
    val lat: Double? = null,
    val lng: Double? = null,
    /** L'outbox : 0 = pas encore reçue par le serveur, 1 = envoyée. */
    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
)

@Entity(
    tableName = "photos",
    foreignKeys = [ForeignKey(EtapeEntity::class, ["id"], ["etapeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("etapeId")],
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val etapeId: Long,
    val path: String,
)

/** Une étape et ses photos, lues ensemble (@Relation : Room fait la 2ᵉ requête). */
data class EtapeWithPhotos(
    @Embedded val etape: EtapeEntity,
    @Relation(parentColumn = "id", entityColumn = "etapeId") val photos: List<PhotoEntity>,
)

/** Un voyage et son nombre d'étapes, calculé par SQLite (la PWA le recomptait en JavaScript). */
data class VoyageWithCount(
    @Embedded val voyage: VoyageEntity,
    val etapesCount: Int,
)

// ---- v2 (2.0) : l'enregistreur de trajet, la fonctionnalité native ----

/** Un trajet GPS rattaché à un voyage. endedAt NULL = en cours d'enregistrement. */
@Entity(
    tableName = "trajets",
    foreignKeys = [ForeignKey(VoyageEntity::class, ["id"], ["voyageId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("voyageId")],
)
data class TrajetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val voyageId: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    /** Mise à jour à chaque point par le service : la liste affiche la distance sans relire tous les points. */
    @ColumnInfo(defaultValue = "0") val distanceM: Double = 0.0,
    /** v3 : le nom donné par l'utilisateur (« De Bavella à Piana »). NULL = pas encore nommé. */
    val nom: String? = null,
)

/** Un relevé GPS. altitude : NULL si le fournisseur n'en donne pas. */
@Entity(
    tableName = "track_points",
    foreignKeys = [ForeignKey(TrajetEntity::class, ["id"], ["trajetId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("trajetId")],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trajetId: Long,
    val lat: Double,
    val lng: Double,
    val altitude: Double?,
    val accuracy: Float,
    val time: Long,
)

data class TrajetWithCount(
    @Embedded val trajet: TrajetEntity,
    val pointsCount: Int,
)

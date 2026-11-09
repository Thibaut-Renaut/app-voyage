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

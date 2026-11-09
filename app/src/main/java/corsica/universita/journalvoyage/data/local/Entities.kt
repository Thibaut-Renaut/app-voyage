package corsica.universita.journalvoyage.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "voyages")
data class VoyageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titre: String,
    val description: String,
    val coverPath: String? = null,
    val createdAt: Long,
)

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

data class EtapeWithPhotos(
    @Embedded val etape: EtapeEntity,
    @Relation(parentColumn = "id", entityColumn = "etapeId") val photos: List<PhotoEntity>,
)

data class VoyageWithCount(
    @Embedded val voyage: VoyageEntity,
    val etapesCount: Int,
)

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
    @ColumnInfo(defaultValue = "0") val distanceM: Double = 0.0,
    val nom: String? = null,
)

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

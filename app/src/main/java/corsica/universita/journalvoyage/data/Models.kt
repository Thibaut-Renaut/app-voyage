package corsica.universita.journalvoyage.data

import java.text.DateFormat
import java.util.Date

data class Voyage(
    val id: Long,
    val titre: String,
    val description: String,
    val coverPath: String?,
    val createdAt: Long,
    val etapesCount: Int = 0,
)

data class Etape(
    val id: Long,
    val voyageId: Long,
    val titre: String,
    val description: String,
    val date: Long,
    val lat: Double?,
    val lng: Double?,
    val synced: Boolean,
    val photos: List<String>,
)

fun Long.toDateTime(): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(this))

data class Trajet(
    val id: Long,
    val voyageId: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val distanceM: Double,
    val pointsCount: Int,
    val nom: String? = null,
)

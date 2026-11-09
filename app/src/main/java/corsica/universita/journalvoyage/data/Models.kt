package corsica.universita.journalvoyage.data

import java.text.DateFormat
import java.util.Date

/** Un voyage tel que l'écran le voit (≠ VoyageEntity, la ligne en base : le repository convertit). */
data class Voyage(
    val id: Long,
    val titre: String,
    val description: String,
    val coverPath: String?,
    val createdAt: Long,
    val etapesCount: Int = 0,
)

/** Une étape et ses photos (des chemins de fichiers dans filesDir/photos/). */
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

/** Date et heure lisibles, au format de la langue du téléphone. */
fun Long.toDateTime(): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(this))

fun Long.toDate(): String = DateFormat.getDateInstance(DateFormat.LONG).format(Date(this))

/** v2 : un trajet enregistré. endedAt == null : en cours. */
data class Trajet(
    val id: Long,
    val voyageId: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val distanceM: Double,
    val pointsCount: Int,
)

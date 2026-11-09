package corsica.universita.journalvoyage.tracking

import java.time.Instant
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Un point du tracé, tel que les calculs et l'écran le voient (sans Android : testable en JUnit pur). */
data class TrackPoint(val lat: Double, val lng: Double, val altitude: Double?, val accuracy: Float, val time: Long)

data class TrackStats(
    val distanceM: Double,
    val durationMs: Long,
    val elevationGainM: Double,
    val avgSpeedKmh: Double,
)

/**
 * Les calculs du tracé, en Kotlin pur (aucune API Android) : testés par TrackMathTest, sur la JVM du PC.
 */
object TrackMath {
    private const val EARTH_RADIUS_M = 6_371_008.8

    /** Distance à vol d'oiseau entre deux points (formule de haversine), en mètres. */
    fun distance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }

    /**
     * Garder un relevé ? Non s'il est trop imprécis (> 50 m : un relevé Wi-Fi en intérieur), ou s'il est
     * à moins de 3 m du précédent (le « bruit » GPS d'un marcheur arrêté gonflerait la distance).
     */
    fun accept(previous: TrackPoint?, next: TrackPoint): Boolean {
        if (next.accuracy > 50f) return false
        if (previous == null) return true
        return distance(previous.lat, previous.lng, next.lat, next.lng) >= 3.0
    }

    fun stats(points: List<TrackPoint>, now: Long? = null): TrackStats {
        if (points.isEmpty()) return TrackStats(0.0, 0, 0.0, 0.0)
        val distance = points.zipWithNext { a, b -> distance(a.lat, a.lng, b.lat, b.lng) }.sum()
        val duration = (now ?: points.last().time) - points.first().time
        val hours = duration / 3_600_000.0
        return TrackStats(
            distanceM = distance,
            durationMs = duration,
            elevationGainM = elevationGain(points.mapNotNull { it.altitude }),
            avgSpeedKmh = if (hours > 0) distance / 1000 / hours else 0.0,
        )
    }

    /**
     * Dénivelé positif cumulé, avec un seuil de 3 m : l'altitude GPS oscille de quelques mètres ;
     * additionner chaque petite montée compterait des dizaines de mètres sur du plat.
     */
    fun elevationGain(altitudes: List<Double>, threshold: Double = 3.0): Double {
        if (altitudes.isEmpty()) return 0.0
        var gain = 0.0
        var reference = altitudes.first()
        for (alt in altitudes.drop(1)) {
            when {
                alt - reference >= threshold -> { gain += alt - reference; reference = alt }
                reference - alt >= threshold -> reference = alt
            }
        }
        return gain
    }

    fun formatDistance(m: Double): String =
        if (m < 1000) String.format(Locale.FRANCE, "%.0f m", m) else String.format(Locale.FRANCE, "%.2f km", m / 1000)

    fun formatDuration(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) String.format(Locale.ROOT, "%d h %02d min", s / 3600, (s % 3600) / 60)
        else String.format(Locale.ROOT, "%d min %02d s", s / 60, s % 60)
    }

    /**
     * Le tracé au format GPX 1.1, que lisent Strava, Komoot, OsmAnd, Google Earth…
     * Les coordonnées en Locale.ROOT : un « 42,7 » à la française rendrait le fichier illisible.
     */
    fun toGpx(name: String, points: List<TrackPoint>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="Journal de Voyage" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        append("  <trk><name>").append(name.xmlEscape()).append("</name><trkseg>\n")
        for (p in points) {
            append(String.format(Locale.ROOT, "    <trkpt lat=\"%.7f\" lon=\"%.7f\">", p.lat, p.lng))
            p.altitude?.let { append(String.format(Locale.ROOT, "<ele>%.1f</ele>", it)) }
            append("<time>").append(Instant.ofEpochMilli(p.time)).append("</time></trkpt>\n")
        }
        append("  </trkseg></trk>\n</gpx>\n")
    }

    private fun String.xmlEscape() =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

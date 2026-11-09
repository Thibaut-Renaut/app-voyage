package corsica.universita.journalvoyage.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMathTest {
    private fun p(lat: Double, lng: Double, alt: Double? = null, acc: Float = 5f, t: Long = 0) = TrackPoint(lat, lng, alt, acc, t)

    @Test
    fun distanceBastiaAjaccio() {
        val d = TrackMath.distance(42.6977, 9.4508, 41.9192, 8.7386)
        assertEquals(105_000.0, d, 2_000.0)
    }

    @Test
    fun unMilliemeDeDegreDeLatitudeFaitEnviron111Metres() {
        assertEquals(111.2, TrackMath.distance(42.0, 9.0, 42.001, 9.0), 0.5)
    }

    @Test
    fun releveImprecisOuImmobileRejete() {
        assertTrue(TrackMath.accept(null, p(42.0, 9.0)))
        assertFalse("précision 80 m", TrackMath.accept(null, p(42.0, 9.0, acc = 80f)))
        assertFalse("à 1 m du précédent", TrackMath.accept(p(42.0, 9.0), p(42.00001, 9.0)))
        assertTrue("à 11 m du précédent", TrackMath.accept(p(42.0, 9.0), p(42.0001, 9.0)))
    }

    @Test
    fun denivelePositifIgnoreLeBruit() {
        assertEquals(0.0, TrackMath.elevationGain(listOf(100.0, 101.0, 100.0, 101.5, 100.2)), 0.01)
        assertEquals(30.0, TrackMath.elevationGain(listOf(100.0, 110.0, 120.0, 130.0, 125.0)), 0.01)
    }

    @Test
    fun statsDistanceDureeVitesse() {
        val pts = listOf(p(42.0, 9.0, t = 0), p(42.001, 9.0, t = 60_000), p(42.002, 9.0, t = 120_000))
        val s = TrackMath.stats(pts)
        assertEquals(222.4, s.distanceM, 1.0)
        assertEquals(120_000L, s.durationMs)
        assertEquals(6.67, s.avgSpeedKmh, 0.05)
    }

    @Test
    fun gpxEnPointDecimalEtEchappe() {
        val gpx = TrackMath.toGpx("Bastia <citadelle> & port", listOf(p(42.7, 9.45, alt = 12.0, t = 0)))
        assertTrue(gpx.contains("""<trkpt lat="42.7000000" lon="9.4500000">"""))
        assertTrue(gpx.contains("<ele>12.0</ele>"))
        assertTrue(gpx.contains("Bastia &lt;citadelle&gt; &amp; port"))
        assertTrue(gpx.contains("<time>1970-01-01T00:00:00Z</time>"))
    }
}

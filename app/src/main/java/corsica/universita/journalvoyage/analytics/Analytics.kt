package corsica.universita.journalvoyage.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import corsica.universita.journalvoyage.data.local.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Mesure d'audience (Firebase Analytics), active uniquement après consentement explicite. */
class Analytics(context: Context, private val settings: Settings, private val scope: CoroutineScope) {
    private val firebase = FirebaseAnalytics.getInstance(context)

    init {
        scope.launch {
            settings.analyticsConsent.distinctUntilChanged().collect {
                firebase.setAnalyticsCollectionEnabled(it == true)
                Log.d(TAG, "Analytics : collecte ${if (it == true) "activée" else "désactivée"} (consentement = $it)")
            }
        }
    }

    suspend fun etapeCreated(photos: Int, withLocation: Boolean) {
        if (settings.claimFirstEtape()) {
            log("first_etape_created", "photos" to photos.toLong(), "with_location" to if (withLocation) 1L else 0L)
        }
    }

    suspend fun sessionStarted() {
        val today = LocalDate.now().toEpochDay()
        val previous = settings.swapSessionDay(today) ?: return
        if (previous != today) log("return_visit", "days_since_last" to (today - previous))
    }

    fun notificationClicked(type: String) = log("notif_clicked", "type" to type)

    fun trajetStarted() = log("trajet_started")

    fun trajetCompleted(points: Int, distanceM: Long, durationS: Long) =
        log("trajet_completed", "points" to points.toLong(), "distance_m" to distanceM, "duration_s" to durationS)

    fun trajetFailed(reason: String) = log("trajet_failed", "reason" to reason)

    /**
     * Le consentement est relu à chaque événement : au démarrage à froid (clic sur une notification),
     * l'événement est émis avant que le collecteur de `init` n'ait lu DataStore.
     */
    private fun log(name: String, vararg params: Pair<String, Any>) {
        val bundle = Bundle().apply {
            params.forEach { (k, v) -> if (v is Long) putLong(k, v) else putString(k, v.toString()) }
        }
        scope.launch {
            val consented = settings.analyticsConsent.first() == true
            Log.d(TAG, "Analytics : $name ${params.toMap()} ${if (consented) "→ Firebase" else "(non envoyé : pas de consentement)"}")
            if (consented) {
                firebase.setAnalyticsCollectionEnabled(true)
                firebase.logEvent(name, bundle)
            }
        }
    }

    private companion object {
        const val TAG = "Journal"
    }
}

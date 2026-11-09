package corsica.universita.journalvoyage.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import corsica.universita.journalvoyage.data.local.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * La mesure : savoir ce que font les utilisateurs, comme pour la PWA — mais ni cookie ni navigateur :
 * le SDK Firebase est dans le process. logEvent() écrit l'événement dans une file sur le disque ;
 * le SDK l'envoie par lots, plus tard (hors ligne, il attend sur le téléphone, comme l'outbox).
 *
 * Trois événements, et les trois seuls que l'app pose elle-même :
 *  1. Activation : `first_etape_created`, le premier geste utile (une fois par installation).
 *  2. Rétention  : `return_visit` (une session un autre jour) et `notif_clicked` (une notification ouverte).
 *  3. Le natif   : `trajet_started`, `trajet_completed`, `trajet_failed` (utilisée ? réussie ?).
 *
 * RGPD : la collecte est coupée dans le manifest (firebase_analytics_collection_enabled = false) et n'est
 * activée qu'après un « Accepter » explicite, gardé dans DataStore. Refus = rien ne part.
 * Chaque événement est aussi écrit dans Logcat (adb logcat -s Journal) : vérifiable sans console Firebase.
 */
class Analytics(context: Context, private val settings: Settings, scope: CoroutineScope) {
    private val firebase = FirebaseAnalytics.getInstance(context)
    private val consent = MutableStateFlow<Boolean?>(null)

    init {
        scope.launch {
            settings.analyticsConsent.distinctUntilChanged().collect {
                consent.value = it
                firebase.setAnalyticsCollectionEnabled(it == true)
                Log.d(TAG, "Analytics : collecte ${if (it == true) "activée" else "désactivée"} (consentement = $it)")
            }
        }
    }

    // ---- 1. Activation ----

    suspend fun etapeCreated(photos: Int, withLocation: Boolean) {
        if (settings.claimFirstEtape()) {
            log("first_etape_created", "photos" to photos.toLong(), "with_location" to if (withLocation) 1L else 0L)
        }
    }

    // ---- 2. Rétention ----

    /** Appelé à chaque lancement de l'Activity (pas à la rotation). */
    suspend fun sessionStarted() {
        val today = LocalDate.now().toEpochDay()
        val previous = settings.swapSessionDay(today) ?: return // première session : rien à mesurer
        if (previous != today) log("return_visit", "days_since_last" to (today - previous))
    }

    /** type : « push » (message FCM), « sync » (compte rendu d'envoi), « trajet » (enregistrement en cours). */
    fun notificationClicked(type: String) = log("notif_clicked", "type" to type)

    // ---- 3. La fonctionnalité native ----

    fun trajetStarted() = log("trajet_started")

    fun trajetCompleted(points: Int, distanceM: Long, durationS: Long) =
        log("trajet_completed", "points" to points.toLong(), "distance_m" to distanceM, "duration_s" to durationS)

    /** reason : permission_denied, location_off, fgs_not_allowed… : un échec mesuré vaut mieux qu'un échec caché. */
    fun trajetFailed(reason: String) = log("trajet_failed", "reason" to reason)

    private fun log(name: String, vararg params: Pair<String, Any>) {
        val bundle = Bundle().apply {
            params.forEach { (k, v) -> if (v is Long) putLong(k, v) else putString(k, v.toString()) }
        }
        val sent = if (consent.value == true) "→ Firebase" else "(non envoyé : pas de consentement)"
        Log.d(TAG, "Analytics : $name ${params.toMap()} $sent")
        firebase.logEvent(name, bundle) // collecte désactivée : le SDK l'ignore
    }

    private companion object {
        const val TAG = "Journal"
    }
}

package corsica.universita.journalvoyage

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessaging
import corsica.universita.journalvoyage.analytics.Analytics
import corsica.universita.journalvoyage.data.JournalRepository
import corsica.universita.journalvoyage.data.PhotoStore
import corsica.universita.journalvoyage.data.local.JournalDatabase
import corsica.universita.journalvoyage.data.local.Settings
import corsica.universita.journalvoyage.data.location.LocationSource
import corsica.universita.journalvoyage.data.remote.okHttp
import corsica.universita.journalvoyage.data.remote.syncApi
import corsica.universita.journalvoyage.notif.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Pas de Hilt : un conteneur à la main. Une seule instance de chaque objet par process, construite
 * avant tout écran, et partagée par les écrans, le SyncWorker et le service FCM.
 */
object JournalContainer {
    lateinit var database: JournalDatabase
        private set
    lateinit var repository: JournalRepository
        private set
    lateinit var settings: Settings
        private set
    lateinit var photoStore: PhotoStore
        private set
    lateinit var locationSource: LocationSource
        private set
    lateinit var analytics: Analytics
        private set

    /** Un scope qui vit avec le process, pour le travail lancé hors d'un écran (jeton FCM, données de démo). */
    val appScope = CoroutineScope(SupervisorJob())

    fun init(context: Context) {
        database = JournalDatabase.build(context)
        settings = Settings(context)
        photoStore = PhotoStore(context)
        locationSource = LocationSource(context)
        analytics = Analytics(context, settings, appScope)
        repository = JournalRepository(database, syncApi(okHttp(context)), WorkManager.getInstance(context), photoStore)
    }
}

/** Un objet Application par process : créé avant toute Activity, tout worker, tout service. */
class JournalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        JournalContainer.init(this)
        Notifications.createChannels(this)
        JournalContainer.appScope.launch {
            JournalContainer.repository.seedIfEmpty()
            // Une étape restée « en attente » (worker abandonné après 5 essais) repart à chaque démarrage.
            JournalContainer.repository.enqueueSync()
        }
        fetchFcmToken()
    }

    /** Le jeton courant (onNewToken n'est appelé qu'à sa création ou à son renouvellement). */
    private fun fetchFcmToken() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d("Journal", "Jeton FCM : $token")
                JournalContainer.appScope.launch { JournalContainer.settings.saveFcmToken(token) }
            }
            .addOnFailureListener { Log.w("Journal", "Jeton FCM indisponible : ${it.message}") }
    }
}

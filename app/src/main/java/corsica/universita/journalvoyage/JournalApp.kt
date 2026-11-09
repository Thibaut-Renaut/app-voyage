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
import corsica.universita.journalvoyage.tracking.TrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
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

    /** Un texte ou une photo reçus d'une autre app, en attente d'être versés dans le formulaire d'étape. */
    val pendingShare = MutableStateFlow<SharedContent?>(null)

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

data class SharedContent(val text: String?, val imageUri: String?)

/** Un objet Application par process : créé avant toute Activity, tout worker, tout service. */
class JournalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        JournalContainer.init(this)
        Notifications.createChannels(this)
        JournalContainer.appScope.launch {
            JournalContainer.repository.seedIfEmpty()
            // Un trajet resté « en cours » d'un process tué par l'OS : clos à l'heure de son dernier point.
            val closed = JournalContainer.repository.closeOrphanTrajets(exceptId = TrackingService.activeTrajetId.value)
            if (closed > 0) Log.w("Journal", "$closed trajet(s) interrompu(s) par la mort du process, clos")
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

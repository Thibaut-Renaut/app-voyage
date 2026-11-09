package corsica.universita.journalvoyage

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessaging
import corsica.universita.journalvoyage.analytics.Analytics
import corsica.universita.journalvoyage.data.GpxExporter
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

/** Injection de dépendances manuelle : une instance par process, partagée par l'UI, le worker et les services. */
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
    lateinit var gpxExporter: GpxExporter
        private set

    val pendingShare = MutableStateFlow<SharedContent?>(null)

    val appScope = CoroutineScope(SupervisorJob())

    fun init(context: Context) {
        database = JournalDatabase.build(context)
        settings = Settings(context)
        photoStore = PhotoStore(context)
        locationSource = LocationSource(context)
        analytics = Analytics(context, settings, appScope)
        gpxExporter = GpxExporter(context)
        repository = JournalRepository(database, syncApi(okHttp(context)), WorkManager.getInstance(context), photoStore)
    }
}

data class SharedContent(val text: String?, val imageUri: String?)

class JournalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        JournalContainer.init(this)
        Notifications.createChannels(this)
        JournalContainer.appScope.launch {
            JournalContainer.repository.seedIfEmpty()
            // Un trajet resté ouvert après la mort du process est clos à l'heure de son dernier point.
            val closed = JournalContainer.repository.closeOrphanTrajets(exceptId = TrackingService.activeTrajetId.value)
            if (closed > 0) Log.w("Journal", "$closed trajet(s) interrompu(s) par la mort du process, clos")
            JournalContainer.repository.enqueueSync()
        }
        fetchFcmToken()
    }

    @Suppress("DEPRECATION")
    // getToken() est déprécié sans remplaçant dans firebase-messaging 25.x.
    private fun fetchFcmToken() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d("Journal", "Jeton FCM : $token")
                JournalContainer.appScope.launch { JournalContainer.settings.saveFcmToken(token) }
            }
            .addOnFailureListener { Log.w("Journal", "Jeton FCM indisponible : ${it.message}") }
    }
}

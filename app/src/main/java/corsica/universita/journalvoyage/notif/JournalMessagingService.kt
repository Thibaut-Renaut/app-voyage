package corsica.universita.journalvoyage.notif

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import corsica.universita.journalvoyage.JournalContainer
import corsica.universita.journalvoyage.R
import kotlinx.coroutines.launch

class JournalMessagingService : FirebaseMessagingService() {
    @Suppress("OVERRIDE_DEPRECATION")
    // Déprécié sans remplaçant dans firebase-messaging 25.x.
    override fun onNewToken(token: String) {
        Log.d(TAG, "Nouveau jeton FCM : $token")
        JournalContainer.appScope.launch { JournalContainer.settings.saveFcmToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "Push reçu de ${message.from} : notification=${message.notification?.title} data=${message.data}")
        val d = message.data
        Notifications.showMessage(
            this,
            title = message.notification?.title ?: d["title"] ?: getString(R.string.app_name),
            body = message.notification?.body ?: d["body"] ?: "",
            etapeId = d["etapeId"]?.toLongOrNull(),
            voyageId = d["voyageId"]?.toLongOrNull(),
        )
    }

    private companion object {
        const val TAG = "Journal"
    }
}

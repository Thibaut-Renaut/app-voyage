package corsica.universita.journalvoyage.notif

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import corsica.universita.journalvoyage.JournalContainer
import corsica.universita.journalvoyage.R
import kotlinx.coroutines.launch

/**
 * Réception des push FCM — l'équivalent du Push API + Service Worker de la PWA. Jamais lancé par l'app :
 * Play Services garde une connexion avec les serveurs de Google pour tout le téléphone, et réveille l'app
 * (même tuée) quand un message arrive.
 *
 * - Message « notification » (la console Firebase) + app en arrière-plan : la bibliothèque Firebase l'affiche
 *   seule (canal « messages », manifest) ; onMessageReceived n'est pas appelé. Au clic, les « données
 *   personnalisées » (etapeId…) arrivent en extras String de MainActivity.
 * - App au premier plan, ou message « data » : livré ici ; l'app construit la notification.
 */
class JournalMessagingService : FirebaseMessagingService() {

    /** Marqué « deprecated » dans firebase-messaging 25.x sans remplaçant : toujours le rappel du nouveau jeton. */
    @Suppress("OVERRIDE_DEPRECATION")
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

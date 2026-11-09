package corsica.universita.journalvoyage.notif

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import corsica.universita.journalvoyage.MainActivity
import corsica.universita.journalvoyage.R

/**
 * Les notifications de l'app. Depuis Android 8, chaque notification appartient à un canal : l'app propose
 * une importance, puis l'utilisateur règle chaque canal lui-même. Depuis Android 13, il faut en plus
 * la permission POST_NOTIFICATIONS.
 */
object Notifications {
    const val CHANNEL_SYNC = "sync"         // compte rendu de synchro : LOW, silencieux
    const val CHANNEL_MESSAGES = "messages" // push FCM : HIGH, son + bannière (aussi le canal par défaut, manifest)
    const val CHANNEL_TRACKING = "trajet"   // v2 : le suivi de trajet en cours, LOW (permanente, silencieuse)

    /** Les extras lus par MainActivity.handleIntent. Testable : adb shell am start -n …/.MainActivity --es etapeId 3 */
    const val EXTRA_ETAPE_ID = "etapeId"
    const val EXTRA_VOYAGE_ID = "voyageId"
    const val EXTRA_TRAJET_ID = "trajetId"
    const val EXTRA_SOURCE = "notifSource"

    private const val ID_SYNC = 1
    private const val TAG = "Journal"

    /** Appelé dans JournalApp.onCreate : le canal doit exister avant la première notification, même un push app fermée. */
    fun createChannels(ctx: Context) {
        val manager = NotificationManagerCompat.from(ctx)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_SYNC, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(ctx.getString(R.string.channel_sync_name))
                .setDescription(ctx.getString(R.string.channel_sync_desc))
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_MESSAGES, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(ctx.getString(R.string.channel_messages_name))
                .setDescription(ctx.getString(R.string.channel_messages_desc))
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_TRACKING, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(ctx.getString(R.string.channel_tracking_name))
                .setDescription(ctx.getString(R.string.channel_tracking_desc))
                .build(),
        )
    }

    /** Permission accordée ET notifications non coupées par l'utilisateur dans les réglages. */
    fun enabled(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** Appelé par le SyncWorker, process sans Activity : le clic ouvre l'app. */
    fun showSyncDone(ctx: Context, count: Int) {
        val notification = NotificationCompat.Builder(ctx, CHANNEL_SYNC)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.notif_sync_title))
            .setContentText(ctx.resources.getQuantityString(R.plurals.notif_sync_text, count, count))
            .setContentIntent(openApp(ctx, requestCode = 0, source = "sync"))
            .setAutoCancel(true)
            .build()
        post(ctx, ID_SYNC, notification) // même id : la nouvelle remplace l'ancienne
    }

    /** Appelé par JournalMessagingService : le clic ouvre l'étape (ou le voyage) visé. */
    fun showMessage(ctx: Context, title: String, body: String, etapeId: Long?, voyageId: Long?) {
        val code = (etapeId ?: voyageId ?: System.currentTimeMillis()).toInt()
        val notification = NotificationCompat.Builder(ctx, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp(ctx, code, "push") {
                etapeId?.let { putExtra(EXTRA_ETAPE_ID, it) }
                voyageId?.let { putExtra(EXTRA_VOYAGE_ID, it) }
            })
            .setAutoCancel(true)
            .build()
        post(ctx, code, notification)
    }

    /**
     * Le PendingIntent : un Intent tout prêt, confié à l'OS, qu'il lancera au clic à la place de l'app
     * (peut-être tuée entre-temps). FLAG_IMMUTABLE : personne ne peut le modifier.
     * requestCode différent par cible : sinon l'OS réutiliserait le premier, et toutes ouvriraient la même étape.
     */
    fun openApp(ctx: Context, requestCode: Int, source: String, extras: Intent.() -> Unit = {}): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SOURCE, source)
            extras()
        }
        return PendingIntent.getActivity(ctx, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** notify() sans canal ou sans permission ne lève rien : la notification est jetée en silence. On vérifie avant. */
    @SuppressLint("MissingPermission") // vérifiée par areNotificationsEnabled()
    fun post(ctx: Context, id: Int, notification: Notification) {
        if (!enabled(ctx)) {
            Log.w(TAG, "Notification non affichée : notifications désactivées ou permission refusée")
            return
        }
        NotificationManagerCompat.from(ctx).notify(id, notification)
        Log.d(TAG, "Notification affichée (id $id)")
    }
}

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

object Notifications {
    const val CHANNEL_SYNC = "sync"
    const val CHANNEL_MESSAGES = "messages"
    const val CHANNEL_TRACKING = "trajet"

    const val EXTRA_ETAPE_ID = "etapeId"
    const val EXTRA_VOYAGE_ID = "voyageId"
    const val EXTRA_TRAJET_ID = "trajetId"
    const val EXTRA_SOURCE = "notifSource"

    private const val ID_SYNC = 1
    private const val TAG = "Journal"

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

    fun enabled(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun showSyncDone(ctx: Context, count: Int) {
        val notification = NotificationCompat.Builder(ctx, CHANNEL_SYNC)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.notif_sync_title))
            .setContentText(ctx.resources.getQuantityString(R.plurals.notif_sync_text, count, count))
            .setContentIntent(openApp(ctx, requestCode = 0, source = "sync"))
            .setAutoCancel(true)
            .build()
        post(ctx, ID_SYNC, notification)
    }

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

    // Un requestCode par cible : sinon Android réutiliserait le premier PendingIntent pour toutes les notifications.
    fun openApp(ctx: Context, requestCode: Int, source: String, extras: Intent.() -> Unit = {}): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SOURCE, source)
            extras()
        }
        return PendingIntent.getActivity(ctx, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    @SuppressLint("MissingPermission")
    fun post(ctx: Context, id: Int, notification: Notification) {
        if (!enabled(ctx)) {
            Log.w(TAG, "Notification non affichée : notifications désactivées ou permission refusée")
            return
        }
        NotificationManagerCompat.from(ctx).notify(id, notification)
        Log.d(TAG, "Notification affichée (id $id)")
    }
}

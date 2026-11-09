package corsica.universita.journalvoyage.tracking

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import corsica.universita.journalvoyage.JournalContainer
import corsica.universita.journalvoyage.R
import corsica.universita.journalvoyage.notif.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Enregistrement d'un trajet GPS en service de premier plan (type location) : la position continue
 * d'être reçue écran éteint, application fermée ou en Doze, avec la seule permission « pendant l'utilisation ».
 */
class TrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository get() = JournalContainer.repository
    private val analytics get() = JournalContainer.analytics
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private var trajetId: Long? = null
    private var startedAt = 0L
    private var last: TrackPoint? = null
    private var distanceM = 0.0
    private var points = 0
    private var starting = false
    private var stopRequested = false
    private var stopping = false

    private val writeLock = Mutex()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::onLocation)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent.getLongExtra(EXTRA_VOYAGE_ID, -1))
            ACTION_STOP -> stop()
        }
        // Pas de redémarrage automatique : un trajet interrompu par le système est clos au lancement suivant.
        return START_NOT_STICKY
    }

    private fun start(voyageId: Long) {
        if (_active.value != null || starting || voyageId < 0) return
        startedAt = System.currentTimeMillis()
        try {
            // Doit être appelé dans les 5 s suivant startForegroundService().
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), foregroundType())
        } catch (e: Exception) {
            Log.e(TAG, "Démarrage du suivi refusé par l'OS : ${e.javaClass.simpleName} — ${e.message}")
            analytics.trajetFailed(if (e is SecurityException) "permission_denied" else "fgs_not_allowed")
            stopSelf()
            return
        }
        starting = true
        scope.launch {
            val id = repository.startTrajet(voyageId)
            trajetId = id
            starting = false
            _active.value = id
            Log.d(TAG, "Trajet $id démarré (voyage $voyageId)")
            analytics.trajetStarted()
            if (stopRequested) stop() else requestUpdates()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000)
            .setMinUpdateIntervalMillis(2_000)
            .setMinUpdateDistanceMeters(5f)
            .build()
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission de localisation retirée pendant le démarrage", e)
            analytics.trajetFailed("permission_denied")
            stop()
        }
    }

    private fun onLocation(loc: Location) {
        val id = trajetId ?: return
        val point = TrackPoint(loc.latitude, loc.longitude, if (loc.hasAltitude()) loc.altitude else null, loc.accuracy, loc.time)
        if (!TrackMath.accept(last, point)) return
        last?.let { distanceM += TrackMath.distance(it.lat, it.lng, point.lat, point.lng) }
        last = point
        points++
        val distance = distanceM
        scope.launch {
            writeLock.withLock { repository.addTrackPoint(id, point, distance) }
        }
        Log.d(TAG, "Point $points : ${point.lat}, ${point.lng} (± ${loc.accuracy} m) — ${TrackMath.formatDistance(distanceM)}")
        Notifications.post(this, NOTIFICATION_ID, notification())
    }

    private fun stop() {
        if (stopping) return
        val id = trajetId
        if (id == null) {
            if (starting) stopRequested = true
            else stopForegroundAndSelf()
            return
        }
        stopping = true
        client.removeLocationUpdates(callback)
        scope.launch {
            writeLock.withLock { repository.finishTrajet(id) }
            val duration = System.currentTimeMillis() - startedAt
            Log.d(TAG, "Trajet $id terminé : $points points, ${TrackMath.formatDistance(distanceM)}, ${TrackMath.formatDuration(duration)}")
            analytics.trajetCompleted(points, distanceM.toLong(), duration / 1000)
            trajetId = null
            _active.value = null
            stopForegroundAndSelf()
        }
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        trajetId?.let { id -> JournalContainer.appScope.launch { repository.finishTrajet(id) } }
        _active.value = null
        scope.cancel()
        super.onDestroy()
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0

    private fun notification(): Notification {
        val text = getString(R.string.tracking_notif_text, TrackMath.formatDistance(distanceM), points)
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, TrackingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.tracking_notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setWhen(startedAt)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(
                Notifications.openApp(this, REQUEST_OPEN, "trajet") {
                    trajetId?.let { putExtra(Notifications.EXTRA_TRAJET_ID, it) }
                },
            )
            .addAction(0, getString(R.string.tracking_stop), stopIntent)
            .build()
    }

    companion object {
        private const val TAG = "Journal"
        private const val ACTION_START = "corsica.universita.journalvoyage.tracking.START"
        private const val ACTION_STOP = "corsica.universita.journalvoyage.tracking.STOP"
        private const val EXTRA_VOYAGE_ID = "voyageId"
        private const val NOTIFICATION_ID = 42
        private const val REQUEST_OPEN = 4242

        private val _active = MutableStateFlow<Long?>(null)

        val activeTrajetId: StateFlow<Long?> = _active.asStateFlow()

        fun start(context: Context, voyageId: Long) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java).setAction(ACTION_START).putExtra(EXTRA_VOYAGE_ID, voyageId),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TrackingService::class.java).setAction(ACTION_STOP))
        }
    }
}

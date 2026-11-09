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
 * LA FONCTIONNALITÉ NATIVE : l'enregistreur de trajet GPS.
 *
 * Un « foreground service » de type location : l'app déclare à l'OS qu'elle fait un travail visible
 * par l'utilisateur (la notification permanente, avec un chronomètre et un bouton « Arrêter »).
 * En échange, l'OS la garde en vie et continue de lui livrer la position — écran éteint, app fermée
 * (balayée des récents), téléphone en Doze — sans permission de localisation « en arrière-plan » :
 * le service est démarré pendant que l'app est au premier plan, et la permission « pendant l'utilisation »
 * couvre tout ce qu'il fait ensuite.
 *
 * Une PWA ne peut pas : watchPosition() s'arrête dès que l'onglet passe en arrière-plan ou que l'écran
 * s'éteint, et aucun Service Worker n'a accès à la géolocalisation.
 *
 * Le service n'a pas d'écran : il écrit chaque point dans Room (via le repository) ; l'écran du trajet
 * observe la table et se dessine en direct. Le service et l'écran ne se parlent jamais directement.
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
    private var starting = false      // le trajet est en train d'être créé en base
    private var stopRequested = false // « Arrêter » reçu pendant ce temps
    private var stopping = false      // un double appui sur « Arrêter » ne clôt pas le trajet deux fois

    /** Les points arrivent sur le main thread ; leur écriture est sérialisée (l'ordre et la distance restent justes). */
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
        // NOT_STICKY : si l'OS tue le process, on ne relance pas un enregistrement « dans le dos » de l'utilisateur ;
        // le trajet orphelin est clos au prochain démarrage (JournalApp → closeOrphanTrajets).
        return START_NOT_STICKY
    }

    private fun start(voyageId: Long) {
        if (_active.value != null || starting || voyageId < 0) return
        startedAt = System.currentTimeMillis()
        // 1. startForeground AVANT tout le reste : l'OS laisse 5 s après startForegroundService(), sinon il tue l'app.
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), foregroundType())
        } catch (e: Exception) {
            // SecurityException : permission de localisation absente (Android 14+ la vérifie ICI).
            // ForegroundServiceStartNotAllowedException (Android 12+) : démarré depuis l'arrière-plan.
            Log.e(TAG, "Démarrage du suivi refusé par l'OS : ${e.javaClass.simpleName} — ${e.message}")
            analytics.trajetFailed(if (e is SecurityException) "permission_denied" else "fgs_not_allowed")
            stopSelf()
            return
        }
        // 2. Le trajet en base, puis les mises à jour de position.
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

    @SuppressLint("MissingPermission") // vérifiée avant le démarrage (PermissionFlow), et par startForeground ci-dessus
    private fun requestUpdates() {
        // Un relevé toutes les 5 s au plus, et seulement après 5 m de déplacement : le GPS ne réveille pas
        // l'app pour rien quand on est arrêté (batterie).
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
        // Le texte de la notification suit le trajet (le chronomètre, lui, tourne seul : setUsesChronometer).
        Notifications.post(this, NOTIFICATION_ID, notification())
    }

    private fun stop() {
        if (stopping) return
        val id = trajetId
        if (id == null) {
            if (starting) stopRequested = true // start() rappellera stop() dès que le trajet existera
            else stopForegroundAndSelf()       // rien à arrêter (service créé par ce seul ACTION_STOP)
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

    /** L'OS détruit le service sans passer par stop() (rare) : le trajet est clos quand même, hors de notre scope. */
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
            .setOngoing(true) // l'utilisateur ne peut pas la balayer : elle EST le contrat avec l'OS
            .setOnlyAlertOnce(true) // mise à jour à chaque point, sans son ni vibration
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

        /** L'id du trajet en cours d'enregistrement dans CE process (null : aucun). Observé par les écrans. */
        val activeTrajetId: StateFlow<Long?> = _active.asStateFlow()

        /** startForegroundService : la promesse faite à l'OS d'appeler startForeground() dans les 5 s. */
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

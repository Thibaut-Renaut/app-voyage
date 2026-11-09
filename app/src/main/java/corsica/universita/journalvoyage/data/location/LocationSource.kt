package corsica.universita.journalvoyage.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Un relevé de position : `accuracy` est le rayon en mètres (« à 12 m près »). */
data class Fix(val lat: Double, val lng: Double, val accuracy: Float, val precise: Boolean)

/**
 * La position ponctuelle (formulaire d’étape), par le fournisseur fusionné des Play Services (GPS, Wi-Fi et antennes).
 * Son API répond par un callback ; suspendCancellableCoroutine en fait une fonction suspend.
 */
class LocationSource(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    private fun has(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // vérifiée juste au-dessus
    suspend fun current(): Fix? {
        val fine = has(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = has(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!fine && !coarse) return null                                   // ni précise ni approximative
        // La précision demandée suit ce que l'utilisateur a accordé.
        val priority = if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val cancel = CancellationTokenSource()
        val loc: Location? = suspendCancellableCoroutine { cont ->
            client.getCurrentLocation(priority, cancel.token)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
            cont.invokeOnCancellation { cancel.cancel() } // coroutine annulée → demande annulée
        } ?: lastKnown() // BALANCED n'allume pas le GPS : sans Wi-Fi ni antenne (émulateur, intérieur), pas de relevé frais
        return loc?.let { Fix(it.latitude, it.longitude, it.accuracy, precise = fine) }
    }

    /** La dernière position connue du système (arrondie par l'OS si seule la position approximative est accordée). */
    @SuppressLint("MissingPermission")
    private suspend fun lastKnown(): Location? = suspendCancellableCoroutine { cont ->
        client.lastLocation
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }
    }
}

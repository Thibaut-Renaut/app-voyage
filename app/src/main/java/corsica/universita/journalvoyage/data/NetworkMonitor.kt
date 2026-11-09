package corsica.universita.journalvoyage.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Les événements `online` / `offline` de la PWA, version Android : le ConnectivityManager prévient
 * quand le réseau par défaut apparaît ou disparaît. Sert à l'indicateur réseau, pas à l'envoi
 * (l'envoi, c'est WorkManager et sa contrainte réseau, qui n'ont pas besoin de l'écran).
 */
fun Context.onlineFlow(): Flow<Boolean> = callbackFlow {
    val cm = getSystemService(ConnectivityManager::class.java)
    // « En ligne » = INTERNET ET VALIDATED (Android a vérifié que l'accès marche vraiment) : le même critère
    // que la contrainte NetworkType.CONNECTED de WorkManager. Un Wi-Fi sans accès réel (portail captif,
    // émulateur après le mode avion) n'est pas « en ligne », et l'indicateur ne le prétend pas.
    fun online(caps: NetworkCapabilities?) = caps != null &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    fun current() = online(cm.getNetworkCapabilities(cm.activeNetwork))
    val callback = object : ConnectivityManager.NetworkCallback() {
        // La validation arrive après onAvailable : c'est onCapabilitiesChanged qui la signale.
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(online(caps)) }
        override fun onLost(network: Network) { trySend(current()) }
    }
    trySend(current())
    cm.registerDefaultNetworkCallback(callback)
    awaitClose { cm.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()

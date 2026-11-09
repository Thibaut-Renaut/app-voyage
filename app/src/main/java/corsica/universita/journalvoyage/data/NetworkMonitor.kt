package corsica.universita.journalvoyage.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

fun Context.onlineFlow(): Flow<Boolean> = callbackFlow {
    val cm = getSystemService(ConnectivityManager::class.java)
    // Même critère que la contrainte réseau de WorkManager : un réseau dont l'accès à Internet a été validé.
    fun online(caps: NetworkCapabilities?) = caps != null &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    fun current() = online(cm.getNetworkCapabilities(cm.activeNetwork))
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(online(caps)) }
        override fun onLost(network: Network) { trySend(current()) }
    }
    trySend(current())
    cm.registerDefaultNetworkCallback(callback)
    awaitClose { cm.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()

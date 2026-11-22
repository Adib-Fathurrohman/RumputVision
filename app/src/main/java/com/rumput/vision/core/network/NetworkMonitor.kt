package com.rumput.vision.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

data class NetworkStatus(
    val isConnected: Boolean,
    /** Wi-Fi or Ethernet: the local URL is worth trying. */
    val isLocalCapable: Boolean,
    /** Identifies the current network so a switch (e.g. Wi-Fi to mobile) triggers re-resolution. */
    val networkId: String?,
) {
    companion object {
        val Disconnected = NetworkStatus(isConnected = false, isLocalCapable = false, networkId = null)
    }
}

interface NetworkMonitor {
    val status: Flow<NetworkStatus>
}

@Singleton
class ConnectivityNetworkMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NetworkMonitor {
    override val status: Flow<NetworkStatus> = callbackFlow {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        fun emitFor(network: Network?, caps: NetworkCapabilities?) {
            val status = if (network == null || caps == null ||
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            ) {
                NetworkStatus.Disconnected
            } else {
                NetworkStatus(
                    isConnected = true,
                    isLocalCapable = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
                    networkId = network.toString(),
                )
            }
            trySend(status)
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = emitFor(network, caps)
            override fun onLost(network: Network) = emitFor(null, null)
        }
        cm.activeNetwork.let { emitFor(it, it?.let(cm::getNetworkCapabilities)) }
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }
        .distinctUntilChanged()
        .conflate()
        .flowOn(Dispatchers.Default)
}

interface ForegroundMonitor {
    /** True while any activity of the app is started. Network work is tied to this. */
    val isForeground: Flow<Boolean>
}

@Singleton
class ProcessForegroundMonitor @Inject constructor() : ForegroundMonitor {
    override val isForeground: Flow<Boolean> = callbackFlow {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                trySend(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                trySend(false)
            }
        }
        lifecycle.addObserver(observer)
        awaitClose { lifecycle.removeObserver(observer) }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Main.immediate)
}

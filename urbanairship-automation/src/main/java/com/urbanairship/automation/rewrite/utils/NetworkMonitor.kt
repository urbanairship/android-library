package com.urbanairship.automation.rewrite.utils

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkRequest
import android.os.Build
import com.urbanairship.UALog
import com.urbanairship.automation.rewrite.DerivedStateFlow
import com.urbanairship.util.Network
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow

internal class NetworkMonitor(
    context: Context,
    network: Network = Network.shared()
) {
    var isConnected: StateFlow<Boolean> = DerivedStateFlow(
        onValue = { network.isConnected(context) },
        updates = callbackFlow {
            val callback = object : NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    this@callbackFlow.trySend(true)
                }

                override fun onLost(network: android.net.Network) {
                    this@callbackFlow.trySend(false)
                }
            }

            val service = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    service.registerDefaultNetworkCallback(callback)
                } else {
                    val networkRequest = NetworkRequest.Builder()
                        .addCapability(NET_CAPABILITY_INTERNET)
                        .build()
                    service.registerNetworkCallback(networkRequest, callback)
                }
            } catch (ex: Exception) {
                UALog.e(ex) { "Failed to subscribe for network status update" }
                this@callbackFlow.trySend(true)
            }

            trySend(network.isConnected(context))
            awaitClose { service.unregisterNetworkCallback(callback) }
        }
    )

    companion object {
        fun shared(context: Context): NetworkMonitor {
            return NetworkMonitor(context)
        }
    }
}

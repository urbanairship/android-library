/* Copyright Airship and Contributors */

package com.urbanairship.automation.utils

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import com.urbanairship.UALog
import com.urbanairship.util.DerivedStateFlow
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
                service.registerDefaultNetworkCallback(callback)
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

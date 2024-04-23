package com.urbanairship.automation.rewrite.utils

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkRequest
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.util.Network
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

internal class NetworkMonitor(
    context: Context = UAirship.getApplicationContext(),
    network: Network = Network.shared()
) {

    var isConnected: Boolean = false
        private set

    private val _stateChannel = Channel<Boolean>(Channel.CONFLATED)
    val updates: ReceiveChannel<Boolean> = _stateChannel

    private var disconnect: (() -> Unit)? = null

    init {
        notifyStateUpdate(network.isConnected(context))

        val callback = object : NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                notifyStateUpdate(true)
            }

            override fun onLost(network: android.net.Network) {
                notifyStateUpdate(false)
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
        }

        disconnect = { service.unregisterNetworkCallback(callback) }
    }

    @VisibleForTesting
    internal fun notifyStateUpdate(update: Boolean) {
        isConnected = update
        _stateChannel.trySend(update)
    }

    fun tearDown() {
        disconnect?.invoke()
    }

    companion object {
        fun shared(): NetworkMonitor {
            return NetworkMonitor()
        }
    }
}

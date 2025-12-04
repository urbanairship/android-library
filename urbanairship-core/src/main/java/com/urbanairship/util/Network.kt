/* Copyright Airship and Contributors */
package com.urbanairship.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.RestrictTo
import com.urbanairship.UALog

/**
 * This class provides information about the device's network state.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Network public constructor() {

    /**
     * Determines whether or not the device has an active network connection.
     *
     * @return `true` if the network has a connection, otherwise
     * `false`.
     */
    public fun isConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            UALog.e("Error fetching network info.")
            return false
        }

        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false

        // Return true if the device has a validated internet connection
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    public companion object {

        private val SHARED = Network()

        public fun shared(): Network {
            return SHARED
        }
    }
}

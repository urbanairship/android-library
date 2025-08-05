/* Copyright Airship and Contributors */
package com.urbanairship.util

import android.content.Context
import android.net.ConnectivityManager
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
        }
        return cm?.activeNetworkInfo?.isConnected == true
    }

    public companion object {

        private val SHARED = Network()

        public fun shared(): Network {
            return SHARED
        }
    }
}

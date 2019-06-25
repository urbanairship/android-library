/* Copyright Airship and Contributors */

package com.urbanairship.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import androidx.annotation.RestrictTo;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;

/**
 * This class provides information about the device's network state.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Network {

    /**
     * Determines whether or not the device has an active network connection.
     *
     * @return <code>true</code> if the network has a connection, otherwise
     * <code>false</code>.
     */
    public static boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager)
                UAirship.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo info;
        if (cm != null) {
            info = cm.getActiveNetworkInfo();
        } else {
            Logger.error("Error fetching network info.");
            return false;
        }

        return info != null && info.isConnected();
    }

}


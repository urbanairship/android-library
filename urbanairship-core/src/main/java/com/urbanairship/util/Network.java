/* Copyright Airship and Contributors */

package com.urbanairship.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;

import com.urbanairship.UALog;
import com.urbanairship.UAirship;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * This class provides information about the device's network state.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Network {

    private static Network SHARED = new Network();

    public static Network shared() {
        return SHARED;
    }

    /**
     * Determines whether or not the device has an active network connection.
     *
     * @return <code>true</code> if the network has a connection, otherwise
     * <code>false</code>.
     */
    public boolean isConnected(@NonNull Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo info;
        if (cm != null) {
            info = cm.getActiveNetworkInfo();
        } else {
            UALog.e("Error fetching network info.");
            return false;
        }

        return info != null && info.isConnected();
    }
}

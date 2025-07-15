/* Copyright Airship and Contributors */
package com.urbanairship.google

import android.content.Context
import com.google.android.gms.common.GoogleApiAvailability

/**
 * Wrapper around Google Play Service Utils.
 */
public object GooglePlayServicesUtilWrapper {

    /**
     * Wraps [com.google.android.gms.common.GooglePlayServicesUtil.isUserRecoverableError].
     */
    @JvmStatic
    public fun isUserRecoverableError(errorCode: Int): Boolean {
        return GoogleApiAvailability.getInstance().isUserResolvableError(errorCode)
    }

    /**
     * Wraps [com.google.android.gms.common.GooglePlayServicesUtil.isGooglePlayServicesAvailable].
     */
    @JvmStatic
    public fun isGooglePlayServicesAvailable(context: Context): Int {
        return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
    }
}

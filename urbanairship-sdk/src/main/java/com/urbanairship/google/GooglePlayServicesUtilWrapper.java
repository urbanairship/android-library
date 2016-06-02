/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.google;

import android.content.Context;
import android.support.annotation.NonNull;

import com.google.android.gms.common.GooglePlayServicesUtil;


/**
 * Wrapper around Google Play Service Utils.
 */
class GooglePlayServicesUtilWrapper {


    /**
     * Wraps {@link com.google.android.gms.common.GooglePlayServicesUtil#isUserRecoverableError(int)}.
     */
    public static boolean isUserRecoverableError(int errorCode) {
        return GooglePlayServicesUtil.isUserRecoverableError(errorCode);
    }

    /**
     * Wraps {@link com.google.android.gms.common.GooglePlayServicesUtil#isGooglePlayServicesAvailable(android.content.Context)}.
     */
    public static int isGooglePlayServicesAvailable(@NonNull Context context) {
        return GooglePlayServicesUtil.isGooglePlayServicesAvailable(context);
    }
}

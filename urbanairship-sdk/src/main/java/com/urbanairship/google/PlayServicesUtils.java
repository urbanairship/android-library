/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.google;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.support.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;

import java.util.List;

/**
 * A utility class to help verify and resolve Google Play services issues.
 */
public class PlayServicesUtils {

    private static final String GOOGLE_PLAY_STORE_PACKAGE_OLD = "com.google.market";
    private static final String GOOGLE_PLAY_STORE_PACKAGE = "com.android.vending";

    /**
     * Value of @value com.google.android.gms.common.ConnectionResult#SUCCESS.
     */
    private static final int CONNECTION_SUCCESS = 0;

    /**
     * Error code returned by {@link PlayServicesUtils#isGooglePlayServicesDependencyAvailable()}
     * when the Google Play services dependency is missing.
     */
    public static final int MISSING_PLAY_SERVICE_DEPENDENCY = -1;


    private static Boolean isGooglePlayServicesDependencyAvailable;
    private static Boolean isGoogleCloudMessagingDependencyAvailable;
    private static Boolean isFusedLocationDependencyAvailable;


    /**
     * Checks and handles any user recoverable Google Play services errors.
     * <p/>
     * </p>
     * If a user recoverable error is encountered, a {@link com.urbanairship.google.PlayServicesErrorActivity}
     * will be launched to display any resolution dialog provided by Google Play
     * services.
     *
     * @param context The application context.
     */
    public static void handleAnyPlayServicesError(@NonNull Context context) {
        if (!isGooglePlayServicesDependencyAvailable()) {
            return;
        }

        int errorCode;

        try {
            errorCode = GooglePlayServicesUtilWrapper.isGooglePlayServicesAvailable(context);
        } catch (IllegalStateException e) {
            Logger.error("Google Play services developer error: " + e.getMessage());
            return;
        }

        if (errorCode == CONNECTION_SUCCESS) {
            return;
        }

        if (GooglePlayServicesUtilWrapper.isUserRecoverableError(errorCode)) {
            Logger.info("Launching Play Services Activity to resolve error.");
            try {
                context.startActivity(new Intent(context, PlayServicesErrorActivity.class));
            } catch (ActivityNotFoundException e) {
                Logger.error(e.getMessage());
            }
        } else {
            Logger.info("Error " + errorCode + " is not user recoverable.");
        }
    }

    /**
     * Verifies that Google Play services dependency is available and the Google
     * Play services version required for the application is installed and enabled
     * on the device.
     * <p/>
     * This method is a wrapper around
     * {@link com.google.android.gms.common.GooglePlayServicesUtil#isGooglePlayServicesAvailable(android.content.Context)}
     * but with an additional check if the dependency is also available.
     *
     * @param context The application context.
     * @return {@link #MISSING_PLAY_SERVICE_DEPENDENCY} if Google Play services dependency is missing,
     * or the errorCode returned by
     * {@link com.google.android.gms.common.GooglePlayServicesUtil#isGooglePlayServicesAvailable(android.content.Context)}
     */
    public static int isGooglePlayServicesAvailable(@NonNull Context context) {
        if (isGooglePlayServicesDependencyAvailable()) {
            return GooglePlayServicesUtilWrapper.isGooglePlayServicesAvailable(context);
        } else {
            return MISSING_PLAY_SERVICE_DEPENDENCY;
        }
    }

    /**
     * Checks if Google Play services dependency is available.
     *
     * @return <code>true</code> if available, otherwise <code>false</code>.
     */
    public static boolean isGooglePlayServicesDependencyAvailable() {
        if (isGooglePlayServicesDependencyAvailable == null) {
            if (Build.VERSION.SDK_INT < 8) {
                isGooglePlayServicesDependencyAvailable = false;
            } else {
                // Play Services
                try {
                    Class.forName("com.google.android.gms.common.GooglePlayServicesUtil");
                    isGooglePlayServicesDependencyAvailable = true;
                } catch (ClassNotFoundException e) {
                    isGooglePlayServicesDependencyAvailable = false;
                }
            }
        }

        return isGooglePlayServicesDependencyAvailable;
    }

    /**
     * Checks if Google Play services dependency is available for GCM.
     *
     * @return <code>true</code> if available, otherwise <code>false</code>.
     */
    public static boolean isGoogleCloudMessagingDependencyAvailable() {
        if (isGoogleCloudMessagingDependencyAvailable == null) {
            if (!isGooglePlayServicesDependencyAvailable()) {
                isGoogleCloudMessagingDependencyAvailable = false;
            } else {
                try {
                    Class.forName("com.google.android.gms.gcm.GoogleCloudMessaging");
                    Class.forName("com.google.android.gms.gcm.GcmReceiver");
                    isGoogleCloudMessagingDependencyAvailable = true;
                } catch (ClassNotFoundException e) {
                    isGoogleCloudMessagingDependencyAvailable = false;
                }
            }
        }

        return isGoogleCloudMessagingDependencyAvailable;
    }

    /**
     * Checks if Google Play services dependency is available for Fused Location.
     *
     * @return <code>true</code> if available, otherwise <code>false</code>.
     * @deprecated Marked to be removed in 7.0.0. Use {@link #isFusedLocationDependencyAvailable()} instead.
     */
    @Deprecated
    public static boolean isFusedLocationDepdendencyAvailable() {
        return isFusedLocationDependencyAvailable();
    }

    /**
     * Checks if Google Play services dependency is available for Fused Location.
     *
     * @return <code>true</code> if available, otherwise <code>false</code>.
     */
    public static boolean isFusedLocationDependencyAvailable() {
        if (isFusedLocationDependencyAvailable == null) {
            if (!isGooglePlayServicesDependencyAvailable()) {
                isFusedLocationDependencyAvailable = false;
            } else {
                try {
                    Class.forName("com.google.android.gms.location.LocationServices");
                    isFusedLocationDependencyAvailable = true;
                } catch (ClassNotFoundException e) {
                    isFusedLocationDependencyAvailable = false;
                }
            }
        }

        return isFusedLocationDependencyAvailable;
    }

    /**
     * Checks if the Google Play Store package is installed on the device.
     *
     * @return <code>true</code> if Google Play Store package is installed on the device,
     * otherwise <code>false</code>
     */
    public static boolean isGooglePlayStoreAvailable() {
        List<PackageInfo> packages = UAirship.getPackageManager().getInstalledPackages(0);
        for (PackageInfo packageInfo : packages) {
            if (packageInfo.packageName.equals(GOOGLE_PLAY_STORE_PACKAGE) ||
                    packageInfo.packageName.equals(GOOGLE_PLAY_STORE_PACKAGE_OLD)) {

                return true;
            }
        }

        return false;
    }
}

/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.google;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.support.annotation.NonNull;

import com.urbanairship.Logger;

import java.lang.reflect.Modifier;

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
    private static Boolean isGooglePlayStoreAvailable;
    private static Boolean isGoogleAdsDependencyAvailable;


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
            // Play Services
            try {
                Class.forName("com.google.android.gms.common.GooglePlayServicesUtil");
                isGooglePlayServicesDependencyAvailable = true;
            } catch (ClassNotFoundException e) {
                isGooglePlayServicesDependencyAvailable = false;
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
     */
    public static boolean isFusedLocationDependencyAvailable() {
        if (isFusedLocationDependencyAvailable == null) {
            if (!isGooglePlayServicesDependencyAvailable()) {
                isFusedLocationDependencyAvailable = false;
            } else {
                try {
                    Class.forName("com.google.android.gms.location.LocationServices");
                    Class googleApiClientClass = Class.forName("com.google.android.gms.common.api.GoogleApiClient");
                    isFusedLocationDependencyAvailable = !Modifier.isInterface(googleApiClientClass.getModifiers());
                } catch (ClassNotFoundException e) {
                    isFusedLocationDependencyAvailable = false;
                }
            }
        }

        return isFusedLocationDependencyAvailable;
    }


    /**
     * Checks if Google Play services dependency is available for advertising ID tracking.
     *
     * @return <code>true</code> if available, otherwise <code>false</code>.
     */
    public static boolean isGoogleAdsDependencyAvailable() {
        if (isGoogleAdsDependencyAvailable == null) {
            if (!isGooglePlayServicesDependencyAvailable()) {
                isGoogleAdsDependencyAvailable = false;
            } else {
                try {
                    Class.forName("com.google.android.gms.ads.identifier.AdvertisingIdClient");
                    isGoogleAdsDependencyAvailable = true;
                } catch (ClassNotFoundException e) {
                    isGoogleAdsDependencyAvailable = false;
                }
            }
        }

        return isGoogleAdsDependencyAvailable;
    }

    /**
     * Checks if the Google Play Store package is installed on the device.
     *
     * @param context The application context.
     * @return <code>true</code> if Google Play Store package is installed on the device,
     * otherwise <code>false</code>
     */
    public static boolean isGooglePlayStoreAvailable(@NonNull Context context) {
        if (isGooglePlayStoreAvailable == null) {
            isGooglePlayStoreAvailable = isPackageAvailable(context, GOOGLE_PLAY_STORE_PACKAGE) || isPackageAvailable(context, GOOGLE_PLAY_STORE_PACKAGE_OLD);
        }
        return isGooglePlayStoreAvailable;
    }

    /**
     * Checks if a given package is installed on the device.
     * @param context The application context.
     * @param packageName The name of the package as a string.
     * @return <code>true</code> if the given package is installed on the device,
     * otherwise <code>false</code>
     */
    private static boolean isPackageAvailable(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (NameNotFoundException e) {
            return false;
        }
    }
}

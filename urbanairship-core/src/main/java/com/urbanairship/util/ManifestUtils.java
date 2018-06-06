/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.util;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;

import com.urbanairship.AirshipReceiver;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;

/**
 * Utility methods for validating the AndroidManifest.xml file.
 */
public class ManifestUtils {

    /**
     * Metadata an app can use to enable local storage.
     */
    public final static String ENABLE_LOCAL_STORAGE = "com.urbanairship.webview.ENABLE_LOCAL_STORAGE";

    /**
     * Database directory for local storage on Android version prior to API 19.
     */
    public final static String LOCAL_STORAGE_DATABASE_DIRECTORY = "com.urbanairship.webview.localstorage";

    /**
     * Returns whether the specified permission is granted for the application or not.
     *
     * @param permission Permission to check.
     * @return <code>true</code> if the permission is granted, otherwise <code>false</code>.
     */
    public static boolean isPermissionGranted(@NonNull String permission) {
        return PackageManager.PERMISSION_GRANTED == UAirship.getPackageManager()
                                                         .checkPermission(permission, UAirship.getPackageName());
    }

    /**
     * Gets the ComponentInfo for an activity
     *
     * @param activity The activity to look up
     * @return The activity's ComponentInfo, or null if the activity
     * is not listed in the manifest
     */
    public static ActivityInfo getActivityInfo(@NonNull Class activity) {
        ComponentName componentName = new ComponentName(UAirship.getPackageName(),
                activity.getCanonicalName());
        try {
            return UAirship.getPackageManager().getActivityInfo(componentName,
                    PackageManager.GET_META_DATA);

        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Gets the ApplicationInfo for the application.
     * @return An instance of ApplicationInfo, or null if the info is unavailable.
     */
    public static ApplicationInfo getApplicationInfo() {
        try {
            return UAirship.getPackageManager().getApplicationInfo(UAirship.getPackageName(),
                    PackageManager.GET_META_DATA);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Helper method to check if local storage should be used.
     *
     * @return {@code true} if local storage should be used, otherwise {@code false}.
     */
    public static boolean shouldEnableLocalStorage() {
        ApplicationInfo info = ManifestUtils.getApplicationInfo();
        if (info != null && info.metaData != null && info.metaData.getBoolean(ENABLE_LOCAL_STORAGE, false)) {
            Logger.verbose("UAWebView - Application contains metadata to enable local storage");
            return true;
        }

        return false;
    }

    /**
     * Determine whether the specified permission is known to the system
     *
     * @param permission the permission name to check (e.g. com.google.android.c2dm.permission.RECEIVE)
     * @return <code>true</code>if known, <code>false</code> otherwise
     */
    public static boolean isPermissionKnown(@NonNull String permission) {
        try {
            UAirship.getPackageManager().getPermissionInfo(permission, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Validates the manifest for Urban Airship components.
     */
    public static void validateManifest() {

        // Validate any app push receivers
        ActivityInfo[] receivers = null;

        try {
            receivers = UAirship.getPackageManager().getPackageInfo(UAirship.getPackageName(), PackageManager.GET_RECEIVERS).receivers;
        } catch (Exception e) {
            Logger.error("Unable to query the application's receivers.", e);
        }

        if (receivers != null) {
            for (ActivityInfo info : receivers) {
                try {
                    Class receiverClass = Class.forName(info.name);
                    if (AirshipReceiver.class.isAssignableFrom(receiverClass)) {
                        if (info.exported) {
                            Logger.error("Receiver " + info.name + " is exported. This might " +
                                    "allow outside applications to message the receiver. Make sure the intent is protected by a " +
                                    "permission or prevent the receiver from being exported.");
                            throw new IllegalStateException("Receiver cannot be exported. Exporting the receiver allows other " +
                                    "apps to send fake broadcasts to this app.");
                        }
                    }
                } catch (ClassNotFoundException e) {
                    Logger.debug("ManifestUtils - Unable to find class: " + info.name, e);
                }
            }
        }
    }
}

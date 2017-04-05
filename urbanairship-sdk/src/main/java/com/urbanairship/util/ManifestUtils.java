/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.util;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;
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
                        }
                    }
                } catch (ClassNotFoundException e) {
                    Logger.debug("ManifestUtils - Unable to find class: " + info.name, e);
                }
            }
        }

        if (!ManifestUtils.isPermissionKnown(UAirship.getUrbanAirshipPermission())) {
            throw new IllegalStateException("Missing required permission: " + UAirship.getUrbanAirshipPermission() + ". Verify the applicationId is set" +
                    "in application's build.gradle file.");
        }
    }
}

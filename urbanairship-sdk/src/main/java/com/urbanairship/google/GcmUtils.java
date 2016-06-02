/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.google;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.push.GcmConstants;
import com.urbanairship.push.GcmPushReceiver;
import com.urbanairship.util.ManifestUtils;

/**
 * Util methods for GCM.
 */
public class GcmUtils {

    /**
     * Required permission to receive GCM messages.
     */
    public static final String PERMISSION_RECEIVE = "com.google.android.c2dm.permission.RECEIVE";

    /**
     * Validates the manifest for GCM.
     */
    public static void validateManifest(@NonNull AirshipConfigOptions options) {
        PackageManager pm = UAirship.getPackageManager();
        String packageName = UAirship.getPackageName();

        ManifestUtils.checkRequiredPermission(Manifest.permission.WAKE_LOCK);

        if (ManifestUtils.isPermissionKnown(PERMISSION_RECEIVE)) {
            ManifestUtils.checkRequiredPermission(PERMISSION_RECEIVE);
        } else {
            Logger.error("Required permission " + PERMISSION_RECEIVE + " is unknown to PackageManager.");
        }

        // GCM messaging permission (ties messages to this app)
        // This permission is not required on devices with API Level 16 and higher
        ApplicationInfo appInfo = UAirship.getPackageInfo().applicationInfo;
        if ((appInfo != null && appInfo.targetSdkVersion < 16) || Build.VERSION.SDK_INT < 16) {

            String permission = packageName + ".permission.C2D_MESSAGE";

            if (ManifestUtils.isPermissionKnown(permission)) {
                ManifestUtils.checkRequiredPermission(permission);
            } else {
                Logger.error("Required permission " + permission + " is unknown to PackageManager.");
            }
        }

        if (PlayServicesUtils.isGoogleCloudMessagingDependencyAvailable()) {
            // GCM intent receiver
            ComponentInfo gcmReceiver = ManifestUtils.getReceiverInfo(GcmPushReceiver.class);

            if (gcmReceiver != null) {
                // next check for receive intent filter with matching category
                Intent receiveIntent = new Intent(GcmConstants.ACTION_GCM_RECEIVE);
                receiveIntent.addCategory(packageName);

                if (pm.queryBroadcastReceivers(receiveIntent, 0).isEmpty()) {
                    Logger.error("AndroidManifest.xml's " + GcmPushReceiver.class.getCanonicalName() +
                            " declaration missing required " + receiveIntent.getAction() +
                            " filter with category = " + packageName);
                }
            } else {
                Logger.error("AndroidManifest.xml missing required receiver: " + GcmPushReceiver.class.getCanonicalName());
            }

            try {
                // isGooglePlayServicesAvailable throws an exception if the
                // manifest does not contain the Google Play services version tag.
                PlayServicesUtils.isGooglePlayServicesAvailable(UAirship.getApplicationContext());
            } catch (IllegalStateException e) {
                Logger.error("Google Play services developer error: " + e.getMessage());
            }
        } else {
            Logger.error("Google Play services required for GCM.");
        }
    }
}

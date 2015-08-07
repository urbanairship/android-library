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
import com.urbanairship.push.GCMConstants;
import com.urbanairship.push.GCMPushReceiver;
import com.urbanairship.util.ManifestUtils;

/**
 * Util methods for GCM.
 */
public class GCMUtils {

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
        ManifestUtils.checkRequiredPermission(Manifest.permission.GET_ACCOUNTS);

        if (ManifestUtils.isPermissionKnown(PERMISSION_RECEIVE)) {
            ManifestUtils.checkRequiredPermission(PERMISSION_RECEIVE);
        } else {
            Logger.error("Required permission " + PERMISSION_RECEIVE + " is unknown to PackageManager.");
        }

        // GCM messaging permission (ties messages to this app)
        // This permission is not required on devices with API Level 16 and higher
        ApplicationInfo appInfo = UAirship.getPackageInfo().applicationInfo;
        if (options.minSdkVersion < 16 ||
                (appInfo != null && appInfo.targetSdkVersion < 16) || Build.VERSION.SDK_INT < 16) {

            String permission = packageName + ".permission.C2D_MESSAGE";

            if (ManifestUtils.isPermissionKnown(permission)) {
                ManifestUtils.checkRequiredPermission(permission);
            } else {
                Logger.error("Required permission " + permission + " is unknown to PackageManager.");
            }
        }

        if (PlayServicesUtils.isGoogleCloudMessagingDependencyAvailable()) {
            // GCM intent receiver
            ComponentInfo gcmReceiver = ManifestUtils.getReceiverInfo(GCMPushReceiver.class);

            if (gcmReceiver != null) {
                // next check for receive intent filter with matching category
                Intent receiveIntent = new Intent(GCMConstants.ACTION_GCM_RECEIVE);
                receiveIntent.addCategory(packageName);

                if (pm.queryBroadcastReceivers(receiveIntent, 0).isEmpty()) {
                    Logger.error("AndroidManifest.xml's " + GCMPushReceiver.class.getCanonicalName() +
                            " declaration missing required " + receiveIntent.getAction() +
                            " filter with category = " + packageName);
                }
            } else {
                Logger.error("AndroidManifest.xml missing required receiver: " + GCMPushReceiver.class.getCanonicalName());
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

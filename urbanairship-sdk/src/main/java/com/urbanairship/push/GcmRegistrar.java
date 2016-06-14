/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.google.GcmUtils;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.util.ManifestUtils;

import java.io.IOException;

/**
 * Handles GCM registration.
 */
class GcmRegistrar {

    /**
     * Starts the registration process for GCM
     *
     * @return <code>true</code> if the registration process started, otherwise
     * <code>false</code>.
     */
    public static boolean register() throws IOException {
        Logger.verbose("Registering with GCM.");

        // Check if GCM is available
        if (!isGcmAvailable()) {
            return false;
        }

        String senderId = UAirship.shared().getAirshipConfigOptions().gcmSender;

        InstanceID instanceID = InstanceID.getInstance(UAirship.getApplicationContext());
        String token = instanceID.getToken(senderId, GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);

        if (token != null && !token.equals(UAirship.shared().getPushManager().getGcmToken())) {
            Logger.info("GCM registration successful security token: " + token);
            UAirship.shared().getPushManager().setGcmToken(token);
            UAirship.shared().getPushManager().setRegisteredGcmSenderId(senderId);
        }

        return true;
    }

    /**
     * Checks to see if GCM is available for the application
     *
     * @return <code>true</code> If GCM is available for the application,
     * otherwise <code>false</code>
     */
    private static boolean isGcmAvailable() {
        if (!PlayServicesUtils.isGoogleCloudMessagingDependencyAvailable()) {
            Logger.error("Google Play services for GCM is unavailable.");
            return false;
        }

        try {
            int playServicesStatus = GooglePlayServicesUtil.isGooglePlayServicesAvailable(UAirship.getApplicationContext());
            if (ConnectionResult.SUCCESS != playServicesStatus) {
                Logger.error("Google Play services is currently unavailable.");
                return false;
            }
        } catch (IllegalStateException e) {
            // Missing version tag
            Logger.error("Unable to register with GCM:  " + e.getMessage());
            return false;
        }

        // Check for permissions - the emulator will not have them, nor with devices without the Google stack
        // such as the Kindle Fire.
        if (!ManifestUtils.isPermissionKnown(GcmUtils.PERMISSION_RECEIVE)) {
            Logger.error(GcmUtils.PERMISSION_RECEIVE + " is unknown to PackageManager. " +
                    "Note that an AVD emulator may not support GCM.");

            Logger.error("If you're running in an emulator, you need to install " +
                    "the appropriate image through the Android SDK and AVM manager. " +
                    "See http://developer.android.com/guide/google/gcm/ for further details.");

            return false;
        }

        // The sender ID is crucial, if we don't have this, GCM is not available
        if (UAirship.shared().getAirshipConfigOptions().gcmSender == null) {
            Logger.error("The GCM sender ID is not set. Unable to register.");
            return false;
        }

        return true;
    }
}

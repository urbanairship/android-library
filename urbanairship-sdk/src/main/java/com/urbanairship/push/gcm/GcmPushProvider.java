/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.gcm;


import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.push.PushMessage;
import com.urbanairship.push.PushProvider;

import java.io.IOException;

/**
 * Gcm push provider.
 *
 * @hide
 */
public class GcmPushProvider implements PushProvider {

    @Override
    public int getPlatform() {
        return UAirship.ANDROID_PLATFORM;
    }

    @Override
    public String getRegistrationToken(@NonNull Context context) throws IOException, SecurityException {
        String senderId = UAirship.shared().getAirshipConfigOptions().gcmSender;

        InstanceID instanceID = InstanceID.getInstance(context);
        return instanceID.getToken(senderId, GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
    }

    @Override
    public boolean isAvailable(@NonNull  Context context) {
        try {
            int playServicesStatus = PlayServicesUtils.isGooglePlayServicesAvailable(context);
            if (ConnectionResult.SUCCESS != playServicesStatus) {
                Logger.error("Google Play services is currently unavailable.");
                return false;
            }
        } catch (IllegalStateException e) {
            // Missing version tag
            Logger.error("Unable to register with GCM: " + e.getMessage());
            return false;
        }

        // The sender ID is crucial, if we don't have this, GCM is not available
        if (UAirship.shared().getAirshipConfigOptions().gcmSender == null) {
            Logger.error("The GCM sender ID is not set. Unable to register.");
            return false;
        }

        return true;
    }

    @Override
    public boolean isSupported(@NonNull Context context) {
        return PlayServicesUtils.isGoogleCloudMessagingDependencyAvailable() && PlayServicesUtils.isGooglePlayStoreAvailable(context);
    }

    @Nullable
    @Override
    public boolean isUrbanAirshipMessage(@NonNull Context context, @NonNull UAirship airship, @NonNull PushMessage message) {
        String sender = message.getExtra("sender", null);
        if (sender != null && !sender.equals(airship.getAirshipConfigOptions().gcmSender)) {
            Logger.info("Ignoring GCM message from sender: " + sender);
            return false;
        }

        return true;
    }



    @Override
    public String toString() {
        return "Gcm Push Provider";
    }
}

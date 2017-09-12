/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.gcm;


import android.content.Context;
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
import java.util.Arrays;
import java.util.List;

/**
 * Gcm push provider.
 *
 * @hide
 */
public class GcmPushProvider implements PushProvider {

    private static final List<String> INVALID_TOKENS = Arrays.asList("MESSENGER", "AP", "null");
    @Override
    public int getPlatform() {
        return UAirship.ANDROID_PLATFORM;
    }

    @Override
    public String getRegistrationToken(@NonNull Context context) throws RegistrationException {
        String senderId = UAirship.shared().getAirshipConfigOptions().gcmSender;
        if (senderId == null) {
            return null;
        }

        InstanceID instanceID = InstanceID.getInstance(context);
        String token;
        try {
            token = instanceID.getToken(senderId, GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
            // Check for invalid tokens: https://github.com/googlesamples/google-services/issues/231
            if (token != null && (INVALID_TOKENS.contains(token) || UAirship.getPackageName().equals(token))) {
                instanceID.deleteToken(senderId, GoogleCloudMessaging.INSTANCE_ID_SCOPE);
                throw new RegistrationException("GCM registration returned an invalid token.", true);
            }

        } catch (IOException e) {
            throw new RegistrationException("GCM registration failed.", true, e);
        } catch (SecurityException se) {
            throw new RegistrationException("GCM registration failed.", false, se);
        }
        return token;
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
        String sender = message.getExtra("from", null);
        boolean isValidSender = false;
        if (sender != null) {
            isValidSender = sender.equals(UAirship.shared().getAirshipConfigOptions().gcmSender);
        }

        return isValidSender && message.containsAirshipKeys();
    }

    @Override
    public String toString() {
        return "Gcm Push Provider";
    }
}

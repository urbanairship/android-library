/* Copyright 2017-18 Urban Airship and Contributors */
package com.urbanairship.push.fcm;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.common.ConnectionResult;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.push.PushMessage;
import com.urbanairship.push.PushProvider;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * FCM push provider.
 *
 * @hide
 */
public class FcmPushProvider implements PushProvider {

    private static final List<String> INVALID_TOKENS = Arrays.asList("MESSENGER", "AP", "null");

    @Override
    public int getPlatform() {
        return UAirship.ANDROID_PLATFORM;
    }

    @Override
    public String getRegistrationToken(@NonNull Context context) throws RegistrationException {
        String senderId = UAirship.shared().getAirshipConfigOptions().getFcmSenderId();
        if (senderId == null) {
            return null;
        }

        String token;
        try {

            FirebaseApp app = FirebaseApp.getInstance();
            if (app == null) {
                throw new RegistrationException("FCM registration failed. FirebaseApp not initialized.", false);
            }

            FirebaseInstanceId instanceId = FirebaseInstanceId.getInstance(app);
            token = instanceId.getToken(senderId, FirebaseMessaging.INSTANCE_ID_SCOPE);

            // Validate the token
            if (token != null && (INVALID_TOKENS.contains(token) || UAirship.getPackageName().equals(token))) {
                instanceId.deleteToken(senderId, FirebaseMessaging.INSTANCE_ID_SCOPE);
                throw new RegistrationException("FCM registration returned an invalid token.", true);
            }
        } catch (IOException e) {
            throw new RegistrationException("FCM registration failed.", true, e);
        }

        return token;
    }

    @Override
    public boolean isAvailable(@NonNull Context context) {
        try {
            int playServicesStatus = PlayServicesUtils.isGooglePlayServicesAvailable(context);
            if (ConnectionResult.SUCCESS != playServicesStatus) {
                Logger.error("Google Play services is currently unavailable.");
                return false;
            }
            if (!isFcmAvailable(context)) {
                return false;
            }
        } catch (IllegalStateException e) {
            // Missing version tag
            Logger.error("Unable to register with FCM: " + e.getMessage());
            return false;
        }
        return true;
    }

    @Override
    public boolean isSupported(@NonNull Context context, @NonNull AirshipConfigOptions configOptions) {
        if (!configOptions.isTransportAllowed(AirshipConfigOptions.GCM_TRANSPORT)) {
            return false;
        }

        if (configOptions.getFcmSenderId() == null) {
            Logger.info("The FCM sender ID is not set. Unable to register for Android push notifications.");
            return false;
        }

        return PlayServicesUtils.isGooglePlayStoreAvailable(context);
    }

    @Nullable
    @Override
    public boolean isUrbanAirshipMessage(@NonNull Context context, @NonNull UAirship airship, @NonNull PushMessage message) {
        String sender = message.getExtra("from", null);
        boolean isValidSender = false;
        if (sender != null) {
            isValidSender = sender.equals(UAirship.shared().getAirshipConfigOptions().getFcmSenderId());
        }

        return isValidSender && message.containsAirshipKeys();
    }

    @Override
    public String toString() {
        return "FCM Push Provider";
    }

    /**
     * Checks if FirebaseApp is set up.
     *
     * Returns true if it is set up. The FirebaseApp must be initialized in the app in order for
     * this to return true.
     *
     * Returns false if it is not set up and makes Firebase unavailable.
     *
     * @param context The application context.
     * @return Boolean
     */
    private boolean isFcmAvailable(Context context) {
        try {
            for (FirebaseApp app : FirebaseApp.getApps(context)) {
                if (app.getName().equals(FirebaseApp.DEFAULT_APP_NAME)) {
                    return true;
                }
            }
        } catch (Exception e) {
            Logger.debug("Unable to get FCM Apps.", e);
        }
        return false;
    }
}
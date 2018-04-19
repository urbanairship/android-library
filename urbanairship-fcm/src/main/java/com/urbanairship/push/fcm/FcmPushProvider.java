/* Copyright 2017-18 Urban Airship and Contributors */
package com.urbanairship.push.fcm;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.firebase.FirebaseApp;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.AirshipVersionInfo;
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
public class FcmPushProvider implements PushProvider, AirshipVersionInfo {

    private static final List<String> INVALID_TOKENS = Arrays.asList("MESSENGER", "AP", "null");

    @Override
    public int getPlatform() {
        return UAirship.ANDROID_PLATFORM;
    }

    @Override
    public String getRegistrationToken(@NonNull Context context) throws RegistrationException {

        String token;
        try {

            FirebaseApp app = FirebaseApp.getInstance();
            if (app == null) {
                throw new RegistrationException("FCM registration failed. FirebaseApp not initialized.", false);
            }

            String senderId = getSenderId(app);
            if (senderId == null) {
                Logger.error("The FCM sender ID is not set. Unable to register for FCM.");
                return null;
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

            FirebaseApp app = FirebaseApp.getInstance();
            if (app == null) {
                Logger.error("Firebase not initialized.");
                return false;
            }

            String senderId = getSenderId(app);
            if (senderId == null) {
                Logger.error("The FCM sender ID is not set. Unable to register for FCM.");
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
        if (!configOptions.isTransportAllowed(AirshipConfigOptions.FCM_TRANSPORT)) {
            return false;
        }

        return PlayServicesUtils.isGooglePlayStoreAvailable(context);
    }

    @Nullable
    @Override
    public boolean isUrbanAirshipMessage(@NonNull Context context, @NonNull UAirship airship, @NonNull PushMessage message) {
        return message.containsAirshipKeys();
    }

    @Nullable
    private String getSenderId(FirebaseApp app) {
        String senderId = UAirship.shared().getAirshipConfigOptions().getFcmSenderId();
        if (senderId != null) {
            return senderId;
        }

        return app.getOptions().getGcmSenderId();
    }

    @Override
    public String toString() {
        return "FCM Push Provider";
    }

    @Override
    public String getAirshipVersion() {
        return BuildConfig.URBAN_AIRSHIP_VERSION;
    }

    @Override
    public String getPackageVersion() {
        return BuildConfig.SDK_VERSION;
    }
}
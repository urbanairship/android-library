/* Copyright Airship and Contributors */

package com.urbanairship.push.fcm;

import android.annotation.SuppressLint;
import android.content.Context;

import com.google.android.gms.common.ConnectionResult;
import com.google.firebase.FirebaseApp;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;
import com.urbanairship.AirshipVersionInfo;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.push.PushProvider;
import com.urbanairship.util.UAStringUtil;

import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * FCM push provider.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class FcmPushProvider implements PushProvider, AirshipVersionInfo {

    private static final List<String> INVALID_TOKENS = Arrays.asList("MESSENGER", "AP", "null");

    @Override
    public int getPlatform() {
        return UAirship.ANDROID_PLATFORM;
    }

    @NonNull
    @Override
    public String getDeliveryType() {
        return PushProvider.FCM_DELIVERY_TYPE;
    }

    // Suppressing MissingFirebaseInstanceTokenRefresh, FirebaseInstanceIdService is deprecated
    // but the lint warning does not seem to check for the new method.
    @SuppressLint("MissingFirebaseInstanceTokenRefresh")
    @Override
    @Nullable
    public String getRegistrationToken(@NonNull Context context) throws RegistrationException {

        String token;
        try {

            FirebaseApp app = FirebaseApp.getInstance();
            String senderId = getSenderId(app);
            if (senderId == null) {
                Logger.error("The FCM sender ID is not set. Unable to register with FCM.");
                return null;
            }

            FirebaseInstanceId instanceId = FirebaseInstanceId.getInstance(app);
            token = instanceId.getToken(senderId, FirebaseMessaging.INSTANCE_ID_SCOPE);

            // Validate the token
            if (token != null && (INVALID_TOKENS.contains(token) || UAirship.getPackageName().equals(token))) {
                instanceId.deleteToken(senderId, FirebaseMessaging.INSTANCE_ID_SCOPE);
                throw new RegistrationException("FCM registration returned an invalid token.", true);
            }
        } catch (Exception e) {
            throw new RegistrationException("FCM registration failed.", true, e);
        }

        return token;
    }

    @Override
    public boolean isAvailable(@NonNull Context context) {
        try {
            int playServicesStatus = PlayServicesUtils.isGooglePlayServicesAvailable(context);
            if (ConnectionResult.SUCCESS != playServicesStatus) {
                Logger.info("Google Play services is currently unavailable.");
                return false;
            }

            FirebaseApp app = FirebaseApp.getInstance();
            String senderId = getSenderId(app);
            if (senderId == null) {
                Logger.error("The FCM sender ID is not set. Unable to register for FCM.");
                return false;
            }
        } catch (Exception e) {
            Logger.error(e, "Unable to register with FCM.");
            return false;
        }
        return true;
    }

    @Override
    public boolean isSupported(@NonNull Context context) {
        return PlayServicesUtils.isGooglePlayStoreAvailable(context);
    }

    @Nullable
    private String getSenderId(@NonNull FirebaseApp app) {
        String senderId = UAirship.shared().getAirshipConfigOptions().fcmSenderId;
        if (!UAStringUtil.isEmpty(senderId)) {
            return senderId;
        }

        return app.getOptions().getGcmSenderId();
    }

    @NonNull
    @Override
    public String toString() {
        return "FCM Push Provider " + getAirshipVersion();
    }

    @NonNull
    @Override
    public String getAirshipVersion() {
        return BuildConfig.AIRSHIP_VERSION;
    }

    @NonNull
    @Override
    public String getPackageVersion() {
        return BuildConfig.SDK_VERSION;
    }

}

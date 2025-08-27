/* Copyright Airship and Contributors */

package com.urbanairship.push.fcm;

import android.content.Context;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.AirshipVersionInfo;
import com.urbanairship.BuildConfig;
import com.urbanairship.UALog;
import com.urbanairship.Airship;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.push.PushProvider;
import com.urbanairship.util.UAStringUtil;

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

    @Override
    public Airship.Platform getPlatform() {
        return Airship.Platform.ANDROID;
    }

    @NonNull
    @Override
    public DeliveryType getDeliveryType() {
        return DeliveryType.FCM;
    }

    @SuppressWarnings("deprecation")
    @Override
    @Nullable
    public String getRegistrationToken(@NonNull Context context) throws RegistrationException {
        FirebaseMessaging firebaseMessaging;
        try {
            firebaseMessaging = getFirebaseMessaging();
        } catch(Exception e) {
            throw new PushProviderUnavailableException("Firebase messaging unavailable: " + e.getMessage(), e);
        }

        try {
            return Tasks.await(firebaseMessaging.getToken());
        } catch (Exception e) {
            throw new RegistrationException("FCM error " + e.getMessage(), true, e);
        }
    }

    @Override
    public boolean isAvailable(@NonNull Context context) {
        try {
            int playServicesStatus = PlayServicesUtils.isGooglePlayServicesAvailable(context);
            if (ConnectionResult.SUCCESS != playServicesStatus) {
                UALog.i("Google Play services is currently unavailable.");
                return false;
            }
        } catch (Exception e) {
            UALog.e(e, "Unable to register with FCM.");
            return false;
        }
        return true;
    }

    @Override
    public boolean isSupported(@NonNull Context context) {
        return PlayServicesUtils.isGooglePlayStoreAvailable(context);
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

    @NonNull
    private static FirebaseMessaging getFirebaseMessaging() throws IllegalStateException {
        AirshipConfigOptions configOptions = Airship.shared().getAirshipConfigOptions();
        if (UAStringUtil.isEmpty(configOptions.fcmFirebaseAppName)) {
            // This will throw an IllegalStateException if firebase is not configured
            return FirebaseMessaging.getInstance();
        } else {
            // This will throw an IllegalStateException if the app name is not registered
            FirebaseApp app = FirebaseApp.getInstance(configOptions.fcmFirebaseAppName);
            return (FirebaseMessaging) app.get(FirebaseMessaging.class);
        }
    }
}

/* Copyright Airship and Contributors */

package com.urbanairship.push.adm;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;

import com.amazon.device.messaging.ADMConstants;
import com.urbanairship.AirshipVersionInfo;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.push.PushProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Adm push provider.
 *
 * @hide
 */
public class AdmPushProvider implements PushProvider, AirshipVersionInfo {

    private static final String AMAZON_SEND_PERMISSION = "com.amazon.device.messaging.permission.SEND";
    private static Boolean isAdmDependencyAvailable;

    @Override
    public int getPlatform() {
        return UAirship.AMAZON_PLATFORM;
    }

    @NonNull
    @Override
    public String getDeliveryType() {
        return PushProvider.ADM_DELIVERY_TYPE;
    }

    @Override
    @Nullable
    public String getRegistrationToken(@NonNull Context context) throws RegistrationException {
        String admId = AdmWrapper.getRegistrationId(context);

        if (admId != null) {
            return admId;
        }

        final RegistrationReceiver registerReceiver = new RegistrationReceiver();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ADMConstants.LowLevel.ACTION_APP_REGISTRATION_EVENT);
        intentFilter.addCategory(context.getPackageName());

        context.registerReceiver(registerReceiver, intentFilter, AMAZON_SEND_PERMISSION, new Handler(Looper.getMainLooper()));
        AdmWrapper.startRegistration(context);

        try {
            registerReceiver.awaitRegistration();
        } catch (InterruptedException e) {
            throw new RegistrationException("ADM registration interrupted", true, e);
        }

        context.unregisterReceiver(registerReceiver);

        if (registerReceiver.error != null) {
            throw new RegistrationException(registerReceiver.error, false);
        }

        return registerReceiver.registrationToken;
    }

    @Override
    public boolean isAvailable(@NonNull Context context) {
        return true;
    }

    @Override
    public boolean isSupported(@NonNull Context context) {
        if (isAdmDependencyAvailable == null) {
            try {
                Class.forName("com.amazon.device.messaging.ADM");
                isAdmDependencyAvailable = true;
            } catch (ClassNotFoundException e) {
                isAdmDependencyAvailable = false;
            }
        }

        if (isAdmDependencyAvailable) {
            return AdmWrapper.isSupported();
        }

        return false;
    }

    @NonNull
    @Override
    public String toString() {
        return "ADM Push Provider " + getAirshipVersion();
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

    private static class RegistrationReceiver extends BroadcastReceiver {

        private static final long REGISTRATION_TIMEOUT_MS = 10000; // 10 seconds

        private String registrationToken;
        private String error;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getExtras() != null && ADMConstants.LowLevel.ACTION_APP_REGISTRATION_EVENT.equals(intent.getAction())) {
                if (intent.getExtras().containsKey(ADMConstants.LowLevel.EXTRA_ERROR)) {
                    Logger.error("ADM error occurred: %s", intent.getExtras().getString(ADMConstants.LowLevel.EXTRA_ERROR));
                    this.error = intent.getExtras().getString(ADMConstants.LowLevel.EXTRA_ERROR);
                } else {
                    this.registrationToken = intent.getStringExtra(ADMConstants.LowLevel.EXTRA_REGISTRATION_ID);
                }
            }

            if (this.isOrderedBroadcast()) {
                setResultCode(Activity.RESULT_OK);
            }

            synchronized (this) {
                this.notifyAll();
            }
        }

        void awaitRegistration() throws InterruptedException {
            synchronized (this) {
                this.wait(REGISTRATION_TIMEOUT_MS);
            }
        }
    }
}

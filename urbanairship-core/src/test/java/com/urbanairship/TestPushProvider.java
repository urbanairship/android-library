/* Copyright Airship and Contributors */

package com.urbanairship;

import android.content.Context;

import com.urbanairship.push.PushProvider;

import androidx.annotation.NonNull;

public class TestPushProvider implements PushProvider {

    public String registrationToken;

    @Override
    public int getPlatform() {
        return UAirship.ANDROID_PLATFORM;
    }

    @NonNull
    @Override
    public String getDeliveryType() {
        return PushProvider.FCM_DELIVERY_TYPE;
    }

    @Override
    public String getRegistrationToken(@NonNull Context context) throws RegistrationException {
        return registrationToken;
    }

    @Override
    public boolean isAvailable(@NonNull Context context) {
        return true;
    }

    @Override
    public boolean isSupported(@NonNull Context context) {
        return true;
    }


}

/* Copyright Airship and Contributors */

package com.urbanairship;

import android.content.Context;

import com.urbanairship.push.PushProvider;

import androidx.annotation.NonNull;

public class TestPushProvider implements PushProvider {

    private final int platform;
    private final String deliveryType;
    public String registrationToken;

    public TestPushProvider() {
        this(UAirship.ANDROID_PLATFORM, PushProvider.FCM_DELIVERY_TYPE);
    }

    public TestPushProvider(@UAirship.Platform int platform, @NonNull @DeliveryType String deliveryType) {
        this.platform = platform;
        this.deliveryType = deliveryType;
    }

    @Override
    public int getPlatform() {
        return platform;
    }

    @NonNull
    @Override
    public String getDeliveryType() {
        return deliveryType;
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

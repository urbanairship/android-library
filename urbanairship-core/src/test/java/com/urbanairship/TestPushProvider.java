/* Copyright Airship and Contributors */

package com.urbanairship;

import android.content.Context;
import androidx.annotation.NonNull;

import com.urbanairship.push.PushMessage;
import com.urbanairship.push.PushProvider;

public class TestPushProvider implements PushProvider {

    public String registrationToken;

    @Override
    public int getPlatform() {
        return UAirship.ANDROID_PLATFORM;
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

    @Override
    public boolean isUrbanAirshipMessage(@NonNull Context context, @NonNull UAirship airship, @NonNull PushMessage message) {
        return true;
    }

}

/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.content.Context;
import android.support.annotation.NonNull;

import com.urbanairship.push.PushMessage;
import com.urbanairship.push.PushProvider;

import java.io.IOException;

public class TestPushProvider implements PushProvider {

    public String registrationToken;

    @Override
    public int getPlatform() {
        return UAirship.ANDROID_PLATFORM;
    }


    @Override
    public String getRegistrationToken(@NonNull Context context) throws IOException, SecurityException {
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

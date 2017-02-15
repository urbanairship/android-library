/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.push.PushMessage;
import com.urbanairship.push.PushProvider;

import java.io.IOException;

public class TestPushProvider implements PushProvider {

    @Override
    public int getPlatform() {
        return UAirship.ANDROID_PLATFORM;
    }

    @Override
    public boolean shouldUpdateRegistration(@NonNull Context context, @NonNull String registrationId) {
        return true;
    }

    @Override
    public void startRegistration(@NonNull Context context) throws IOException, SecurityException {
    }

    @Override
    public boolean isAvailable(@NonNull Context context) {
        return true;
    }

    @Override
    public boolean isSupported(@NonNull Context context) {
        return true;
    }

    @Nullable
    @Override
    public PushMessage processMessage(@NonNull Context context, @NonNull Bundle pushBundle) {
        return new PushMessage(pushBundle);
    }
}

/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.adm;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.UAirship;
import com.urbanairship.push.PushMessage;
import com.urbanairship.push.PushProvider;
import com.urbanairship.push.PushProviderBridge;

import java.io.IOException;

/**
 * Adm push provider.
 *
 * @hide
 */
public class AdmPushProvider implements PushProvider {

    private static Boolean isAdmDependencyAvailable;


    @Override
    public int getPlatform() {
        return UAirship.AMAZON_PLATFORM;
    }

    @Override
    public boolean shouldUpdateRegistration(@NonNull Context context, @NonNull String registrationId) {
        return !registrationId.equals(AdmWrapper.getRegistrationId(context));
    }

    @Override
    public void startRegistration(@NonNull Context context) throws IOException, SecurityException {
        String admId = AdmWrapper.getRegistrationId(context);

        if (admId == null) {
            AdmWrapper.startRegistration(context);
        } else {
            PushProviderBridge.registrationFinished(context, AdmPushProvider.class, admId);
        }
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

    @Nullable
    @Override
    public PushMessage processMessage(@NonNull Context context, @NonNull Bundle pushBundle) {
        return new PushMessage(pushBundle);
    }

    @Override
    public String toString() {
        return "Adm Push Provider";
    }
}

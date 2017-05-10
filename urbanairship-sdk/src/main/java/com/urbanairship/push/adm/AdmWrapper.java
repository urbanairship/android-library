/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.adm;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.amazon.device.messaging.ADM;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;

/**
 * Wrapper around ADM methods.
 */
class AdmWrapper {

    /**
     * Wraps {@link ADM#isSupported()}.
     *
     * @return The value returned by {@link ADM#isSupported()}.
     */
    public static boolean isSupported() {
        try {
            return new ADM(UAirship.getApplicationContext()).isSupported();
        } catch (RuntimeException ex) {
            Logger.error("Failed to call ADM. Make sure ADM jar is not bundled with the APK.");
            return false;
        }
    }

    /**
     * Wraps {@link ADM#startRegister()}.
     */
    public static void startRegistration(@NonNull Context context) {
        new ADM(context).startRegister();
    }

    /**
     * Wraps {@link ADM#getRegistrationId()}.
     *
     * @param context The application context.
     * @return The registration ID or null if ADM has not registered yet.
     */
    @Nullable
    public static String getRegistrationId(Context context) {
        return new ADM(context).getRegistrationId();
    }
}

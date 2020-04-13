/* Copyright Airship and Contributors */

package com.urbanairship.push.adm;

import android.content.Context;

import com.amazon.device.messaging.ADM;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Wrapper around ADM methods.
 */
class AdmWrapper {

    /**
     * Wraps {@link ADM#isSupported()}.
     *
     * @return The value returned by {@link ADM#isSupported()}.
     */
    static boolean isSupported() {
        try {
            return new ADM(UAirship.getApplicationContext()).isSupported();
        } catch (RuntimeException ex) {
            Logger.warn("Failed to call ADM. Make sure ADM jar is not bundled with the APK.");
            return false;
        }
    }

    /**
     * Wraps {@link ADM#startRegister()}.
     */
    static void startRegistration(@NonNull Context context) {
        new ADM(context).startRegister();
    }

    /**
     * Wraps {@link ADM#getRegistrationId()}.
     *
     * @param context The application context.
     * @return The registration ID or null if ADM has not registered yet.
     */
    @Nullable
    static String getRegistrationId(Context context) {
        return new ADM(context).getRegistrationId();
    }

}

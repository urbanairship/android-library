/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.amazon;

import android.content.Context;
import android.support.annotation.NonNull;

import com.urbanairship.Logger;

/**
 * Util methods for ADM.
 */
public class AdmUtils {

    private static Boolean isAdmAvailable;

    /**
     * Checks if ADM is available on the device.
     *
     * @return <code>true</code> if ADM is available.
     */
    public static boolean isAdmAvailable() {
        if (isAdmAvailable != null) {
            return isAdmAvailable;
        }

        try {
            Class.forName("com.amazon.device.messaging.ADM");
            isAdmAvailable = true;
        } catch (ClassNotFoundException e) {
            isAdmAvailable = false;
        }

        return isAdmAvailable;
    }

    /**
     * Checks if ADM is available and supported on the device.
     *
     * @return <code>true</code> if ADM is available and supported.
     */
    public static boolean isAdmSupported() {
        return isAdmAvailable() && AdmWrapper.isSupported();
    }

    /**
     * Starts the registration process for ADM.
     */
    public static void startRegistration(@NonNull Context context) {
        if (isAdmSupported()) {
            AdmWrapper.startRegistration(context);
        }
    }

    /**
     * Gets the ADM registration ID.
     * @return The registration ID or null if ADM has not registered yet.
     */
    public static String getRegistrationId(@NonNull Context context) {
        if (isAdmSupported()) {
            return AdmWrapper.getRegistrationId(context);
        }
        return null;
    }

    /**
     * Validates the manifest for ADM.
     */
    public static void validateManifest() {
        if (isAdmAvailable()) {
            AdmWrapper.validateManifest();
        } else {
            Logger.warn("ADM is not available on this device.");
        }
    }
}

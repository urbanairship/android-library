/* Copyright Airship and Contributors */

package com.ad4screen.sdk;

import android.content.Context;

import com.ad4screen.sdk.service.modules.profile.DeviceInformation;

import androidx.annotation.NonNull;

/**
 * Accengage's A4S class, with methods mapped to Airship.
 */
public class A4S {

    private static A4S instance;

    private A4S() {}

    /**
     * Gets the instance.
     *
     * @param context The context.
     * @return The instance.
     */
    @NonNull
    public static A4S get(@NonNull final Context context) {
        if (instance != null) {
            instance = new A4S();
        }
        return instance;
    }

    /**
     * Updates device information.
     *
     * @param deviceInformation The device information updates.
     */
    public void updateDeviceInformation(@NonNull final DeviceInformation deviceInformation) {
        deviceInformation.getEditor().apply();
    }

}

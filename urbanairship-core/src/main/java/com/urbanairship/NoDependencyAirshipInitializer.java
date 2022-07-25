/* Copyright Airship and Contributors */

package com.urbanairship;

import android.app.Application;
import android.content.Context;

import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.startup.Initializer;

/**
 * Airship initializer without any external dependencies. Useful if the application wants to initialize
 * work manager using a dependency injection framework. When using this you should remove {@link AirshipInitializer}
 * and add this one by adding the following to the manifest:
 * ```
 * <provider
 *     android:name="androidx.startup.InitializationProvider"
 *     android:authorities="${applicationId}.androidx-startup"
 *     android:exported="false"
 *     tools:node="merge">
 *     <meta-data android:name="com.urbanairship.AirshipInitializer"
 *               tools:node="remove" />
 *     <meta-data  android:name="com.urbanairship.NoDependencyAirshipInitializer"
 *           android:value="androidx.startup" />
 * </provider>
 * ```
 */
public class NoDependencyAirshipInitializer implements Initializer<Boolean> {

    @NonNull
    @Override
    public Boolean create(@NonNull Context context) {
        Autopilot.automaticTakeOff((Application) context.getApplicationContext(), true);
        return UAirship.isTakingOff() || UAirship.isFlying();
    }

    @NonNull
    @Override
    public List<Class<? extends Initializer<?>>> dependencies() {
        return Collections.emptyList();
    }

}

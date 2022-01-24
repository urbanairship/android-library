/* Copyright Airship and Contributors */

package com.urbanairship;

import android.app.Application;
import android.content.Context;

import com.urbanairship.app.GlobalActivityMonitor;

import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.startup.Initializer;
import androidx.work.WorkManagerInitializer;

/**
 * Airship initializer.
 * @hide
 */
public class AirshipInitializer implements Initializer<Boolean> {

    @NonNull
    @Override
    public Boolean create(@NonNull Context context) {
        GlobalActivityMonitor.shared(context.getApplicationContext());
        Autopilot.automaticTakeOff((Application) context.getApplicationContext(), true);
        return UAirship.isTakingOff() || UAirship.isFlying();
    }

    @NonNull
    @Override
    public List<Class<? extends Initializer<?>>> dependencies() {
        return Collections.singletonList(WorkManagerInitializer.class);
    }
}

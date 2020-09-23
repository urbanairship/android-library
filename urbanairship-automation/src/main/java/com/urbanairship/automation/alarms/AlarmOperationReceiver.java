/* Copyright Airship and Contributors */

package com.urbanairship.automation.alarms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Alarm receiver.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AlarmOperationReceiver extends BroadcastReceiver {

    static final String ACTION = "com.urbanairship.automation.alarms.ALARM_FIRED";

    @Override
    public void onReceive(@NonNull Context context, @Nullable Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) {
            return;
        }

        AlarmOperationScheduler.shared(context).onAlarmFired();
    }
}

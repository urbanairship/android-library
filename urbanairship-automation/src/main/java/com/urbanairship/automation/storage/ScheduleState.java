/* Copyright Airship and Contributors */

package com.urbanairship.automation.storage;

import androidx.annotation.RestrictTo;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface ScheduleState {

    // Schedule is active
    int IDLE = 0;

    // Schedule is waiting for its time delay to expire
    int TIME_DELAYED = 5;

    // Schedule is being prepared by the adapter
    int PREPARING_SCHEDULE = 6;

    // Schedule is waiting for app state conditions to be met
    int WAITING_SCHEDULE_CONDITIONS = 1;

    // Schedule is executing
    int EXECUTING = 2;

    // Schedule finished executing and is now waiting for its execution interval to expire
    int PAUSED = 3;

    // Schedule is either expired or at its execution limit
    int FINISHED = 4;

}

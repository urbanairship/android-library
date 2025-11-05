/* Copyright Airship and Contributors */
package com.urbanairship.automation.storage

import androidx.annotation.RestrictTo

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public enum class ScheduleState(public val value: Int) {
    // Schedule is active
    IDLE(0),

    // Schedule is waiting for its time delay to expire
    TIME_DELAYED(5),

    // Schedule is being prepared by the adapter
    PREPARING_SCHEDULE(6),

    // Schedule is waiting for app state conditions to be met
    WAITING_SCHEDULE_CONDITIONS(1),

    // Schedule is executing
    EXECUTING(2),

    // Schedule finished executing and is now waiting for its execution interval to expire
    PAUSED(3),

    // Schedule is either expired or at its execution limit
    FINISHED(4);

    public companion object {
        public fun fromValue(value: Int): ScheduleState? {
            return entries.firstOrNull { it.value == value }
        }
    }
}

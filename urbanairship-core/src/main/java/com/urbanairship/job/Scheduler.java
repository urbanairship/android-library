/* Copyright Airship and Contributors */

package com.urbanairship.job;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Scheduler interface used by {@link JobDispatcher}.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface Scheduler {

    /**
     * Schedules a jobInfo.
     *
     * @param context The application context.
     * @param jobInfo The jobInfo to schedule.
     * @param delayMs The initial delay.
     * @throws SchedulerException if the scheduler fails to schedule the jobInfo.
     */
    void schedule(@NonNull Context context, @NonNull JobInfo jobInfo, long delayMs) throws SchedulerException;
}

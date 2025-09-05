/* Copyright Airship and Contributors */
package com.urbanairship.job

import android.content.Context
import androidx.annotation.RestrictTo
import kotlin.time.Duration

/**
 * Scheduler interface used by [JobDispatcher].
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun interface Scheduler {

    /**
     * Schedules a jobInfo.
     *
     * @param context The application context.
     * @param jobInfo The jobInfo to schedule.
     * @param delay The initial delay.
     * @throws SchedulerException if the scheduler fails to schedule the jobInfo.
     */
    @Throws(SchedulerException::class)
    public fun schedule(context: Context, jobInfo: JobInfo, delay: Duration)
}

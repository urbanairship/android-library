/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.job;

import android.content.Context;
import android.support.annotation.NonNull;

/**
 * Scheduler interface used by {@link JobDispatcher}.
 */
interface Scheduler {

    /**
     * Cancels a scheduled job.
     *
     * @param context The application context.
     * @param tag The job tag to cancel.
     * @throws SchedulerException if the scheduler fails to cancel.
     */
    void cancel(@NonNull Context context, @NonNull String tag) throws SchedulerException;

    /**
     * Schedules a jobInfo.
     *
     * @param context The application context.
     * @param jobInfo The jobInfo to schedule.
     * @throws SchedulerException if the scheduler fails to schedule the jobInfo.
     */
    void schedule(@NonNull Context context, @NonNull JobInfo jobInfo) throws SchedulerException;

    /**
     * Checks if the jobInfo requires scheduling.
     *
     * @param jobInfo The jobInfo.
     * @return {@code true} if the jobInfo should be scheduled, otherwise {@code false}.
     */
    boolean requiresScheduling(@NonNull Context context, @NonNull JobInfo jobInfo);

    /**
     * Called when the jobInfo needs to be rescheduled.
     *
     * @param context The application context.
     * @param jobInfo The jobInfo.
     * @throws SchedulerException if the scheduler fails to schedule the jobInfo.
     */
    void reschedule(@NonNull Context context, @NonNull JobInfo jobInfo) throws SchedulerException;
}
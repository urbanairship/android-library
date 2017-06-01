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
     * Schedules a job.
     *
     * @param context The application context.
     * @param job The job to schedule.
     * @throws SchedulerException if the scheduler fails to schedule the job.
     */
    void schedule(@NonNull Context context, @NonNull Job job) throws SchedulerException;

    /**
     * Checks if the job requires scheduling.
     *
     * @param job The job.
     * @return {@code true} if the job should be scheduled, otherwise {@code false}.
     */
    boolean requiresScheduling(@NonNull Context context, @NonNull Job job);

    /**
     * Called when the job needs to be rescheduled.
     *
     * @param context The application context.
     * @param job The job.
     * @throws SchedulerException if the scheduler fails to schedule the job.
     */
    void reschedule(@NonNull Context context, @NonNull Job job) throws SchedulerException;
}
/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.job;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Scheduler interface used by {@link JobDispatcher}.
 */
interface Scheduler {

    /**
     * Cancels a scheduled job.
     *
     * @param context The application context.
     * @param scheduleId The jobInfo's mapped ID.
     * @throws SchedulerException if the scheduler fails to cancel.
     */
    void cancel(@NonNull Context context, @NonNull int scheduleId) throws SchedulerException;

    /**
     * Schedules a jobInfo.
     *
     * @param context The application context.
     * @param jobInfo The jobInfo to schedule.
     * @param scheduleId The jobInfo's mapped ID.
     * @throws SchedulerException if the scheduler fails to schedule the jobInfo.
     */
    void schedule(@NonNull Context context, @NonNull JobInfo jobInfo, int scheduleId) throws SchedulerException;


    /**
     * Called when the job needs to be rescheduled.
     *
     * @param context The application context.
     * @param jobInfo The jobInfo.
     * @param extras Scheduler extras.
     * @param scheduleId The jobInfo's mapped ID.
     * @throws SchedulerException if the scheduler fails to reschedule the job.
     */
    void reschedule(@NonNull Context context, @NonNull JobInfo jobInfo, int scheduleId, @Nullable Bundle extras) throws SchedulerException;
}
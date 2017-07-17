/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.job;

import android.Manifest;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.OneoffTask;
import com.google.android.gms.gcm.Task;
import com.urbanairship.Logger;
import com.urbanairship.util.ManifestUtils;

import java.util.concurrent.TimeUnit;

/**
 * GcmNetworkManager scheduler.
 */
class GcmScheduler implements Scheduler {

    private static final long WINDOW_EXECUTION_SECONDS = 30; // 30 seconds
    private static final long INITIAL_RETRY_SECONDS = 10; // 10 seconds.

    @Override
    public void cancel(@NonNull Context context, @NonNull int scheduleId) throws SchedulerException {
        try {
            GcmNetworkManager.getInstance(context).cancelTask("scheduleId:" + scheduleId, AirshipGcmTaskService.class);
        } catch (RuntimeException e) {
            throw new SchedulerException("GcmScheduler failed to cancel job.", e);
        }
    }

    @Override
    public void reschedule(@NonNull Context context, @NonNull JobInfo jobInfo, int scheduleId, Bundle extras) throws SchedulerException {
        scheduleJob(context, jobInfo, scheduleId, INITIAL_RETRY_SECONDS);
    }

    @Override
    public void schedule(@NonNull Context context, @NonNull JobInfo jobInfo, int scheduleId) throws SchedulerException {
        long windowStart = TimeUnit.MILLISECONDS.toSeconds(jobInfo.getInitialDelay());
        scheduleJob(context, jobInfo, scheduleId, windowStart);
    }

    /**
     * Helper method to schedule a jobInfo with GcmNetworkManager.
     *
     * @param context The application context.
     * @param jobInfo The jobInfo.
     * @param scheduleId The job schedule ID.
     * @param secondsDelay Minimum amount of time in seconds to delay the jobInfo.
     * @throws SchedulerException if the schedule fails.
     */
    private void scheduleJob(@NonNull Context context, @NonNull JobInfo jobInfo, int scheduleId, long secondsDelay) throws SchedulerException {
        OneoffTask.Builder builder = new OneoffTask.Builder()
                .setService(AirshipGcmTaskService.class)
                .setExtras(jobInfo.toBundle())
                .setTag("scheduleId:" + scheduleId)
                .setUpdateCurrent(true)
                .setExecutionWindow(secondsDelay, secondsDelay + WINDOW_EXECUTION_SECONDS);

        if (jobInfo.isPersistent() && ManifestUtils.isPermissionGranted(Manifest.permission.RECEIVE_BOOT_COMPLETED)) {
            builder.setPersisted(true);
        }

        if (jobInfo.isNetworkAccessRequired()) {
            builder.setRequiredNetwork(Task.NETWORK_STATE_CONNECTED);
        }

        try {
            OneoffTask task = builder.build();
            Logger.verbose("GcmScheduler: Scheduling task: " + task + " for jobInfo: " + jobInfo + " scheduleId: " +scheduleId);
            GcmNetworkManager.getInstance(context).schedule(task);
        } catch (RuntimeException e) {
            // https://issuetracker.google.com/issues/37113668
            throw new SchedulerException("GcmScheduler failed to schedule jobInfo.", e);
        }
    }
}

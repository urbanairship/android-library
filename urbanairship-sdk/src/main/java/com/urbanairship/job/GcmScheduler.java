/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.job;

import android.Manifest;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
    private static final String EXTRA_GCM_TASK = "EXTRA_GCM_TASK";

    public void cancel(@NonNull Context context, @NonNull String tag) {
        GcmNetworkManager.getInstance(context).cancelTask(tag, AirshipGcmTaskService.class);
    }

    @Override
    public boolean requiresScheduling(@NonNull Context context, @NonNull JobInfo jobInfo) {
        if (jobInfo.getInitialDelay() > 0) {
            return true;
        }

        if (jobInfo.isNetworkAccessRequired()) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork == null || !activeNetwork.isConnectedOrConnecting()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void reschedule(@NonNull Context context, @NonNull JobInfo jobInfo) throws SchedulerException {
        if (jobInfo.getSchedulerExtras().getBoolean(EXTRA_GCM_TASK, false)) {
            // Retry is handled by GcmNetworkManager
            return;
        }

        scheduleJob(context, jobInfo, INITIAL_RETRY_SECONDS);
    }


    @Override
    public void schedule(@NonNull Context context, @NonNull JobInfo jobInfo) throws SchedulerException {
        long windowStart = TimeUnit.MILLISECONDS.toSeconds(jobInfo.getInitialDelay());
        scheduleJob(context, jobInfo, windowStart);
    }

    /**
     * Helper method to schedule a jobInfo with GcmNetworkManager.
     *
     * @param context The application context.
     * @param jobInfo The jobInfo.
     * @param secondsDelay Minimum amount of time in seconds to delay the jobInfo.
     *
     * @throws SchedulerException if the schedule fails.
     */
    private void scheduleJob(@NonNull Context context, @NonNull JobInfo jobInfo, long secondsDelay) throws SchedulerException {
        jobInfo.getSchedulerExtras().putBoolean(EXTRA_GCM_TASK, true);

        OneoffTask.Builder builder = new OneoffTask.Builder()
                .setService(AirshipGcmTaskService.class)
                .setExtras(jobInfo.toBundle())
                .setTag(jobInfo.getTag())
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

            Logger.verbose("GcmScheduler: Scheduling task: " + task + " for jobInfo: " + jobInfo);
            GcmNetworkManager.getInstance(context).schedule(task);
        } catch (RuntimeException e) {
            // https://issuetracker.google.com/issues/37113668
            throw new SchedulerException("GcmScheduler failed to schedule jobInfo.", e);
        }

    }
}

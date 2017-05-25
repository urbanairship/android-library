/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.job;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.annotation.NonNull;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.OneoffTask;
import com.google.android.gms.gcm.Task;
import com.urbanairship.Logger;

import java.util.UUID;
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
    public boolean requiresScheduling(@NonNull Context context, @NonNull Job job) {
        if (job.getInitialDelay() > 0) {
            return true;
        }

        if (job.isNetworkAccessRequired()) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork == null || !activeNetwork.isConnectedOrConnecting()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void reschedule(@NonNull Context context, @NonNull Job job) {
        if (!job.getSchedulerExtras().getBoolean(EXTRA_GCM_TASK, false)) {
            // Retry is handled by GcmNetworkManager
            return;
        }

        scheduleJob(context, job, INITIAL_RETRY_SECONDS);
    }

    @Override
    public void schedule(@NonNull Context context, @NonNull Job job) {
        long windowStart = TimeUnit.MILLISECONDS.toSeconds(job.getInitialDelay());
        scheduleJob(context, job, windowStart);
    }

    /**
     * Helper method to schedule a job with GcmNetworkManager.
     *
     * @param context The application context.
     * @param job The job.
     * @param secondsDelay Minimum amount of time in seconds to delay the job.
     */
    private void scheduleJob(@NonNull Context context, @NonNull Job job, long secondsDelay) {
        job.getSchedulerExtras().putBoolean(EXTRA_GCM_TASK, true);

        OneoffTask.Builder builder = new OneoffTask.Builder()
                .setService(AirshipGcmTaskService.class)
                .setExtras(job.toBundle())
                .setTag(job.getTag() == null ? UUID.randomUUID().toString() : job.getTag())
                .setUpdateCurrent(true)
                .setExecutionWindow(secondsDelay, secondsDelay + WINDOW_EXECUTION_SECONDS);

        if (job.isNetworkAccessRequired()) {
            builder.setRequiredNetwork(Task.NETWORK_STATE_CONNECTED);
        }

        OneoffTask task = builder.build();

        Logger.verbose("GcmScheduler: Scheduling task: " + task + " for job: " + job);
        GcmNetworkManager.getInstance(context).schedule(task);
    }
}

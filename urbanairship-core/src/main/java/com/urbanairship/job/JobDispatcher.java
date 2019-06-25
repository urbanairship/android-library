/* Copyright Airship and Contributors */

package com.urbanairship.job;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.app.GlobalActivityMonitor;

/**
 * Dispatches jobs. When a job is dispatched with a delay or specifies that it requires network activity,
 * it will be scheduled using either the AlarmManager or GcmNetworkManager. When a job is finally performed,
 * it will call {@link com.urbanairship.AirshipComponent#onPerformJob(UAirship, JobInfo)}
 * for the component the job specifies.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class JobDispatcher {

    /**
     * Manifest metadata to offset the JOB IDs.
     */
    private static final String JOB_ID_START_KEY = "com.urbanairship.job.JOB_ID_START";

    /**
     * Default job ID offset.
     */
    private static final int DEFAULT_JOB_ID_START = 3000000;

    private final Context context;

    @SuppressLint("StaticFieldLeak")
    private static JobDispatcher instance;

    private final SchedulerFactory schedulerFactory;
    private final ActivityMonitor activityMonitor;

    private Scheduler scheduler;
    private boolean isUsingFallbackScheduler = false;
    private Integer jobIdStart;

    /**
     * Gets the shared instance.
     *
     * @param context The application context.
     * @return The JobDispatcher.
     */
    @NonNull
    public static JobDispatcher shared(@NonNull Context context) {
        if (instance == null) {
            synchronized (JobDispatcher.class) {
                if (instance == null) {
                    instance = new JobDispatcher(context);
                }
            }
        }

        return instance;
    }

    private JobDispatcher(@NonNull Context context) {
        this(context, new DefaultSchedulerFactory(), GlobalActivityMonitor.shared(context));
    }

    @VisibleForTesting
    JobDispatcher(@NonNull Context context, SchedulerFactory schedulerFactory, ActivityMonitor activityMonitor) {
        this.context = context.getApplicationContext();
        this.schedulerFactory = schedulerFactory;
        this.activityMonitor = activityMonitor;
    }

    /**
     * Dispatches a jobInfo to be performed immediately.
     *
     * @param jobInfo The jobInfo.
     */
    public void dispatch(@NonNull JobInfo jobInfo) {
        try {

            if (requiresScheduling(jobInfo)) {
                getScheduler().schedule(context, jobInfo, getScheduleId(jobInfo.getId()));
                return;
            }

            // Otherwise start the service directly
            try {
                getScheduler().cancel(context, jobInfo.getId());
                context.startService(AirshipService.createIntent(context, jobInfo, null));
            } catch (SecurityException | IllegalStateException ex) {
                getScheduler().schedule(context, jobInfo, getScheduleId(jobInfo.getId()));
            }
        } catch (SchedulerException e) {
            Logger.error(e, "Scheduler failed to schedule jobInfo");

            if (useFallbackScheduler()) {
                dispatch(jobInfo);
            }
        }
    }

    /**
     * Helper method to reschedule jobs.
     *
     * @param jobInfo The jobInfo.
     * @param extras Scheduler extras.
     */
    void reschedule(@NonNull JobInfo jobInfo, @Nullable Bundle extras) {
        try {
            getScheduler().reschedule(context, jobInfo, getScheduleId(jobInfo.getId()), extras);
        } catch (SchedulerException e) {
            Logger.error(e, "Scheduler failed to schedule jobInfo");

            if (useFallbackScheduler()) {
                reschedule(jobInfo, extras);
            }
        }
    }

    /**
     * Cancels a job based on the job's ID.
     *
     * @param jobId The job's ID.
     */
    public void cancel(int jobId) {
        try {
            getScheduler().cancel(context, getScheduleId(jobId));
        } catch (SchedulerException e) {
            Logger.error(e, "Scheduler failed to cancel job with id: %s", jobId);

            if (useFallbackScheduler()) {
                cancel(jobId);
            }
        }
    }

    /**
     * Returns the scheduler.
     *
     * @return The scheduler.
     */
    private Scheduler getScheduler() {
        if (scheduler == null) {
            scheduler = schedulerFactory.createScheduler(context);
        }

        return scheduler;
    }

    /**
     * Checks if the job info requires scheduling.
     *
     * @param jobInfo The job info.
     * @return {@code true} if the job should be scheduled, otherwise {@code false}.
     */
    private boolean requiresScheduling(@NonNull JobInfo jobInfo) {
        if (!activityMonitor.isAppForegrounded()) {
            return true;
        }

        if (jobInfo.getInitialDelay() > 0) {
            return true;
        }

        if (jobInfo.isNetworkAccessRequired()) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork == null || !activeNetwork.isConnected();
        }

        return false;
    }

    /**
     * Tries to fallback on a different scheduler.
     *
     * @return {@code true} if the scheduler changed, otherwise {@code false}.
     */
    private boolean useFallbackScheduler() {
        if (isUsingFallbackScheduler) {
            return false;
        }

        scheduler = schedulerFactory.createFallbackScheduler(context);
        isUsingFallbackScheduler = true;
        return true;
    }

    /**
     * Maps a job ID to a scheduler ID.
     *
     * @param jobId The job ID.
     * @return A scheduler ID.
     */
    private int getScheduleId(int jobId) {
        if (jobIdStart == null) {
            ApplicationInfo ai = null;
            try {
                ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            } catch (PackageManager.NameNotFoundException e) {
                Logger.error("Failed get application info.");
            }

            if (ai != null && ai.metaData != null) {
                jobIdStart = ai.metaData.getInt(JOB_ID_START_KEY, DEFAULT_JOB_ID_START);
            } else {
                jobIdStart = DEFAULT_JOB_ID_START;
            }
        }

        return jobId + jobIdStart;
    }

    /**
     * Scheduler factory.
     */
    @VisibleForTesting
    interface SchedulerFactory {

        /**
         * Creates a scheduler.
         *
         * @param context The application context.
         * @return The scheduler.
         */
        @NonNull
        Scheduler createScheduler(Context context);

        /**
         * Creates a fallback scheduler.
         *
         * @param context The application context.
         * @return The fallback scheduler.
         */
        @NonNull
        Scheduler createFallbackScheduler(Context context);

    }

    /**
     * Default scheduler factory.
     */
    private static class DefaultSchedulerFactory implements SchedulerFactory {

        @NonNull
        @Override
        public Scheduler createScheduler(Context context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                return new AndroidJobScheduler();
            }

            return new AlarmScheduler();
        }

        @NonNull
        @Override
        public Scheduler createFallbackScheduler(Context context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                return new AndroidJobScheduler();
            }

            return new AlarmScheduler();
        }

    }

}
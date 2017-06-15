/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.job;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.annotation.VisibleForTesting;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.google.android.gms.common.ConnectionResult;
import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipService;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.util.UAStringUtil;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


/**
 * Dispatches jobs. When a job is dispatched with a delay or specifies that it requires network activity,
 * it will be scheduled using either the AlarmManager or GcmNetworkManager. When a job is finally performed,
 * it will call {@link com.urbanairship.AirshipComponent#onPerformJob(UAirship, Job)}
 * for the component the job specifies.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class JobDispatcher {


    private static final long AIRSHIP_WAIT_TIME_MS = 5000; // 5 seconds.

    private final Context context;
    private static JobDispatcher instance;
    private Scheduler scheduler;
    private boolean isGcmScheduler = false;

    Executor executor = Executors.newSingleThreadExecutor();

    /**
     * Callback when a job is finished.
     */
    public interface Callback {

        /**
         * Called when a job is finished.
         *
         * @param job The job.
         * @param result The job's result.
         */
        void onFinish(Job job, @Job.JobResult int result);
    }

    /**
     * Gets the shared instance.
     *
     * @param context The application context.
     * @return The JobDispatcher.
     */
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

    @VisibleForTesting
    JobDispatcher(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @VisibleForTesting
    JobDispatcher(@NonNull Context context, @NonNull Scheduler scheduler) {
        this.context = context.getApplicationContext();
        this.scheduler = scheduler;
    }

    /**
     * Dispatches a jobInfo to be performed immediately with a wakelock. The wakelock will
     * automatically be released once the jobInfo finishes. The jobInfo will not have a wakelock on
     * retries.
     *
     * @param jobInfo The jobInfo.
     * @return {@code true} if the jobInfo was able to be dispatched with a wakelock, otherwise {@code false}.
     */
    public boolean wakefulDispatch(@NonNull JobInfo jobInfo) {
        if (getScheduler().requiresScheduling(context, jobInfo)) {
            Logger.error("JobDispatcher - Unable to wakefulDispatch with a jobInfo that requires scheduling.");
            return false;
        }

        Intent intent = AirshipService.createIntent(context, jobInfo);
        try {
            WakefulBroadcastReceiver.startWakefulService(context, intent);
            if (jobInfo.getTag() != null) {
                cancel(jobInfo.getTag());
            }
            return true;
        } catch (IllegalStateException e) {
            WakefulBroadcastReceiver.completeWakefulIntent(intent);
            return false;
        }
    }


    /**
     * Dispatches a jobInfo to be performed immediately.
     *
     * @param jobInfo The jobInfo.
     */
    public void dispatch(@NonNull JobInfo jobInfo) {
        try {
            // Cancel any jobs with the same tag
            if (jobInfo.getTag() != null) {
                cancel(jobInfo.getTag());
            }

            if (getScheduler().requiresScheduling(context, jobInfo)) {
                getScheduler().schedule(context, jobInfo);
                return;
            }

            // Otherwise start the service directly
            try {
                context.startService(AirshipService.createIntent(context, jobInfo));
            } catch (IllegalStateException ex) {
                getScheduler().schedule(context, jobInfo);
            }
        } catch (SchedulerException e) {
            Logger.error("Scheduler failed to schedule jobInfo", e);

            if (isGcmScheduler) {
                Logger.info("Falling back to Alarm Scheduler.");
                scheduler = new AlarmScheduler();
                isGcmScheduler = false;
                dispatch(jobInfo);
            }
        }
    }

    /**
     * Helper method to reschedule jobs.
     *
     * @param jobInfo The jobInfo.
     */
    private void reschedule(JobInfo jobInfo) {
        try {
            getScheduler().reschedule(context, jobInfo);
        } catch (SchedulerException e) {
            Logger.error("Scheduler failed to schedule jobInfo", e);

            if (isGcmScheduler) {
                Logger.info("Falling back to Alarm Scheduler.");
                scheduler = new AlarmScheduler();
                isGcmScheduler = false;
                reschedule(jobInfo);
            }
        }
    }


    /**
     * Cancels a job based on the job's tag.
     *
     * @param tag The job's tag.
     */
    public void cancel(@NonNull String tag) {
        try {
            getScheduler().cancel(context, tag);
        } catch (SchedulerException e) {
            Logger.error("Scheduler failed to cancel job with tag: " + tag, e);

            if (isGcmScheduler) {
                Logger.info("Falling back to Alarm Scheduler.");
                scheduler = new AlarmScheduler();
                isGcmScheduler = false;
                cancel(tag);
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

            try {
                if (PlayServicesUtils.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS && PlayServicesUtils.isGoogleCloudMessagingDependencyAvailable()) {
                    scheduler = new GcmScheduler();
                    isGcmScheduler = true;
                } else {
                    scheduler = new AlarmScheduler();
                }
            } catch (IllegalStateException e) {
                scheduler = new AlarmScheduler();
            }
        }

        return scheduler;
    }

    /**
     * Runs a job.
     *
     * @param job The job to run.
     */
    public void runJob(@NonNull final Job job) {
        runJob(job, null);
    }

    /**
     * Runs a job.
     *
     * @param job The job to run.
     * @param callback Callback when the job is finished.
     */
    public void runJob(@NonNull final Job job, @Nullable final Callback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final UAirship airship = UAirship.waitForTakeOff(AIRSHIP_WAIT_TIME_MS);
                if (airship == null) {
                    Logger.error("JobDispatcher - UAirship not ready. Rescheduling job: " + job);
                    if (callback != null) {
                        callback.onFinish(job, Job.JOB_RETRY);
                    }

                    reschedule(job.getJobInfo());

                    return;
                }

                final AirshipComponent component = findAirshipComponent(airship, job.getJobInfo().getAirshipComponentName());
                if (component == null) {
                    Logger.error("JobDispatcher - Unavailable to find airship components for job: " + job);
                    if (callback != null) {
                        callback.onFinish(job, Job.JOB_FINISHED);
                    }
                    return;
                }

                component.getJobExecutor(job).execute(new Runnable() {
                    @Override
                    public void run() {
                        int result = component.onPerformJob(airship, job);
                        Logger.verbose("JobDispatcher - Job finished: " + job + " with result: " + result);

                        if (result == Job.JOB_RETRY) {
                            reschedule(job.getJobInfo());
                        }

                        if (callback != null) {
                            callback.onFinish(job, result);
                        }
                    }
                });
            }
        });
    }

    /**
     * Finds the {@link AirshipComponent}s for a given job.
     *
     * @param componentClassName The component's class name.
     * @param airship The airship instance.
     * @return The airship component.
     */
    private AirshipComponent findAirshipComponent(UAirship airship, String componentClassName) {
        if (UAStringUtil.isEmpty(componentClassName)) {
            return null;
        }

        for (final AirshipComponent component : airship.getComponents()) {
            if (component.getClass().getName().equals(componentClassName)) {
                return component;
            }
        }

        return null;
    }
}

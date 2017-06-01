/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.job;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.google.android.gms.common.ConnectionResult;
import com.urbanairship.AirshipService;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.google.PlayServicesUtils;


/**
 * Dispatches jobs. When a job is dispatched with a delay or specifies that it requires network activity,
 * it will be scheduled using either the AlarmManager or GcmNetworkManager. When a job is finally performed,
 * it will start the {@link AirshipService}. The {@link AirshipService} will call {@link com.urbanairship.AirshipComponent#onPerformJob(UAirship, Job)}
 * for the component the job specifies.
 *
 * @hide
 */
public class JobDispatcher {

    static final String EXTRA_JOB_DISPATCHED = "EXTRA_JOB_DISPATCHED";

    private final Context context;
    private static JobDispatcher instance;
    private Scheduler scheduler;
    private boolean isGcmScheduler = false;

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
     * Dispatches a job to be performed immediately with a wakelock. The wakelock will
     * automatically be released once the job finishes. The job will not have a wakelock on
     * retries.
     *
     * @param job The job.
     */
    public void wakefulDispatch(@NonNull Job job) {
        if (job.getSchedulerExtras().getBoolean(EXTRA_JOB_DISPATCHED, false)) {
            Logger.error("JobDispatcher - Unable to wakefulDispatch with a job that requires rescheduling.");
            dispatch(job);
            return;
        }

        job.getSchedulerExtras().putBoolean(EXTRA_JOB_DISPATCHED, true);

        if (getScheduler().requiresScheduling(context, job)) {
            Logger.error("JobDispatcher - Unable to wakefulDispatch with a job that requires scheduling.");
            dispatch(job);
            return;
        }

        if (job.getTag() != null) {
            cancel(job.getTag());
        }

        WakefulBroadcastReceiver.startWakefulService(context, AirshipService.createIntent(context, job));
    }

    /**
     * Dispatches a job to be performed immediately.
     *
     * @param job The job.
     */
    public void dispatch(@NonNull Job job) {
        try {
            dispatchHelper(job);
        } catch (SchedulerException e) {
            Logger.error("Scheduler failed to schedule job", e);

            if (isGcmScheduler) {
                Logger.info("Falling back to Alarm Scheduler.");
                scheduler = new AlarmScheduler();
                isGcmScheduler = false;
                dispatch(job);
            }
        }
    }

    /**
     * Helper method to dispatch jobs.
     *
     * @param job The job.
     */
    private void dispatchHelper(Job job) throws SchedulerException {
        // If it was already dispatched reschedule the job
        if (job.getSchedulerExtras().getBoolean(EXTRA_JOB_DISPATCHED, false)) {
            getScheduler().reschedule(context, job);
            return;
        }

        // If it requires scheduling due to delay or network connectivity schedule the job
        if (getScheduler().requiresScheduling(context, job)) {
            getScheduler().schedule(context, job);
            job.getSchedulerExtras().putBoolean(EXTRA_JOB_DISPATCHED, true);
            return;
        }

        // Cancel any jobs with the same tag
        if (job.getTag() != null) {
            cancel(job.getTag());
        }

        // Otherwise start the service directly
        job.getSchedulerExtras().putBoolean(EXTRA_JOB_DISPATCHED, true);
        context.startService(AirshipService.createIntent(context, job));
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
}

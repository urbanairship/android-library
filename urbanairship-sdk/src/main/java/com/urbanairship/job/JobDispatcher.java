/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.job;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.urbanairship.AirshipService;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;

import java.util.concurrent.TimeUnit;

/**
 * Dispatches jobs. Jobs can be either dispatched to be performed right away, or with a delay. When
 * a job is dispatched with a delay it will be scheduled using the AlarmManager. A job will start
 * the {@link AirshipService} where the component defined by the job will receive the dispatched job
 * in the {@link com.urbanairship.AirshipComponent#onPerformJob(UAirship, Job)}.
 *
 * @hide
 */
public class JobDispatcher {

    private final Context context;
    private static JobDispatcher instance;

    /**
     * Gets the shared instance.
     *
     * @param context The application context.
     * @return The JobDispatcher.
     */
    public static JobDispatcher shared(Context context) {
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
    JobDispatcher(Context context) {
        this.context = context;
    }

    /**
     * Dispatches a job to be performed immediately.
     *
     * @param job The job.
     */
    public void dispatch(@NonNull Job job) {
        cancel(job.getAction());
        context.startService(createJobIntent(job, 0));
    }

    /**
     * Dispatches a job to be performed immediately with a wakelock. The wakelock will
     * automatically be released once the job finishes. The job will not have a wakelock on
     * retries.
     *
     * @param job The job.
     */
    public void wakefulDispatch(@NonNull Job job) {
        cancel(job.getAction());
        WakefulBroadcastReceiver.startWakefulService(context, createJobIntent(job, 0));
    }

    /**
     * Dispatches a job to be performed at a later date.
     *
     * @param job The job.
     * @param delay The delay.
     * @param delayUnit The delay time unit.
     */
    public void dispatch(@NonNull Job job, long delay, TimeUnit delayUnit) {
        long delayMillis = delayUnit.toMillis(delay);
        if (delayMillis <= 0) {
            dispatch(job);
            return;
        }

        Intent intent = createJobIntent(job, delayMillis);

        // Schedule the intent
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        try {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + delayMillis, pendingIntent);
        } catch (SecurityException e) {
            Logger.error("JobDispatcher - Failed to schedule intent " + intent.getAction(), e);
        }
    }

    /**
     * Cancels a job based on the job's action.
     *
     * @param action The job's action.
     */
    public void cancel(String action) {
        Intent intent = new Intent(context, AirshipService.class)
                .setAction(action);

        PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_NO_CREATE);
        if (pendingIntent != null) {
            AlarmManager alarmManager = (AlarmManager) context
                    .getSystemService(Context.ALARM_SERVICE);

            alarmManager.cancel(pendingIntent);
        }
    }

    /**
     * Checks if a job is scheduled with the given job action.
     *
     * @param action The job's action.
     */
    public boolean isScheduled(String action) {
        Intent intent = new Intent(context, AirshipService.class)
                .setAction(action);

        return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_NO_CREATE) != null;
    }

    /**
     * Creates an {@link AirshipService} intent.
     *
     * @param job The job.
     * @param delay The job's delay.
     * @return An intent for the {@link AirshipService}.
     */
    private Intent createJobIntent(Job job, long delay) {
        return new Intent(context, AirshipService.class)
                .setAction(job.getAction())
                .putExtra(AirshipService.EXTRA_AIRSHIP_COMPONENT, job.getAirshipComponentName())
                .putExtra(AirshipService.EXTRA_JOB_EXTRAS, job.getExtras())
                .putExtra(AirshipService.EXTRA_DELAY, delay);
    }
}

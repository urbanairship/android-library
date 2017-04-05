/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.WorkerThread;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.urbanairship.job.Job;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.util.UAStringUtil;

import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


/**
 * Urban Airship Service.
 *
 * @hide
 */
public class AirshipService extends Service {

    /**
     * The default starting back off time for retries in milliseconds
     */
    protected static final long DEFAULT_STARTING_BACK_OFF_TIME_MS = 10000; // 10 seconds.

    /**
     * The default max back off time for retries in milliseconds.
     */
    protected static final long DEFAULT_MAX_BACK_OFF_TIME_MS = 5120000; // About 85 mins.

    /**
     * Time to wait for UAirship when processing messages.
     */
    private static final long AIRSHIP_WAIT_TIME_MS = 10000; // 10 seconds

    public static final String EXTRA_AIRSHIP_COMPONENT = "EXTRA_AIRSHIP_COMPONENT";
    public static final String EXTRA_JOB_EXTRAS = "EXTRA_JOB_EXTRAS";
    public static final String EXTRA_DELAY = "EXTRA_DELAY";

    private static final int MSG_INTENT_RECEIVED = 1;
    private static final int MSG_INTENT_JOB_FINISHED = 2;

    private Looper looper;
    private IncomingHandler handler;
    private int lastStartId = 0;
    private int runningJobs;

    private static HashMap<String, Executor> executors = new HashMap<>();

    private final class IncomingHandler extends Handler {
        IncomingHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_INTENT_RECEIVED:
                    onHandleIntent((Intent) msg.obj, msg.arg1);
                    break;

                case MSG_INTENT_JOB_FINISHED:
                    onJobFinished((Intent) msg.obj, msg.arg1);
                    break;
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        HandlerThread thread = new HandlerThread("Airship Service");
        thread.start();

        looper = thread.getLooper();
        handler = new IncomingHandler(looper);
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, final int startId) {
        Message msg = handler.obtainMessage();
        msg.what = MSG_INTENT_RECEIVED;
        msg.arg1 = startId;
        msg.obj = intent;

        Logger.debug("AirshipService - Received intent: " + intent);
        handler.sendMessage(msg);

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        looper.quit();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @WorkerThread
    private void onHandleIntent(final Intent intent, int startId) {
        this.lastStartId = startId;

        final Message msg = handler.obtainMessage();
        msg.what = MSG_INTENT_JOB_FINISHED;
        msg.arg1 = startId;
        msg.obj = intent;

        if (intent == null || intent.getAction() == null) {
            handler.sendMessage(msg);
            return;
        }

        Logger.debug("AirshipService - Starting tasks for intent: " + intent.getAction() + " taskId: " + startId);

        final UAirship airship = UAirship.waitForTakeOff(AIRSHIP_WAIT_TIME_MS);
        if (airship == null) {
            Logger.error("AirshipService - UAirship not ready. Dropping intent: " + intent);
            handler.sendMessage(msg);
            return;
        }

        final String componentName = intent.getStringExtra(EXTRA_AIRSHIP_COMPONENT);
        final Bundle extras = intent.getBundleExtra(EXTRA_JOB_EXTRAS);
        final long delay = intent.getLongExtra(EXTRA_DELAY, 0);
        final AirshipComponent component = findAirshipComponent(airship, componentName);

        if (component == null) {
            Logger.error("AirshipService - Unavailable to find airship components for job with action: " + intent.getAction());
            handler.sendMessage(msg);
            return;
        }

        final Job job = Job.newBuilder(intent.getAction())
                           .setAirshipComponent(component.getClass())
                           .setExtras(extras)
                           .build();

        Executor executor = executors.get(component.getClass().getName());
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor();
            executors.put(componentName, executor);
        }

        runningJobs++;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                int result = component.onPerformJob(airship, job);
                if (result == Job.JOB_RETRY) {

                    long backOff = delay;
                    if (backOff <= 0) {
                        backOff = DEFAULT_STARTING_BACK_OFF_TIME_MS;
                    } else {
                        backOff = Math.min(delay * 2, DEFAULT_MAX_BACK_OFF_TIME_MS);
                    }

                    JobDispatcher.shared(getApplicationContext())
                                 .dispatch(job, backOff, TimeUnit.MILLISECONDS);

                    handler.sendMessage(msg);
                }
            }
        });
    }

    /**
     * Called when a job is finished.
     *
     * @param intent The original intent.
     * @param startId The intent's startId.
     */
    @WorkerThread
    private void onJobFinished(Intent intent, int startId) {
        Logger.verbose("AirshipService - Component finished job with startId: " + startId);

        runningJobs--;

        if (intent != null) {
            WakefulBroadcastReceiver.completeWakefulIntent(intent);
        }

        if (runningJobs <= 0) {
            runningJobs = 0;
            Logger.verbose("AirshipService - All jobs finished, stopping with last startId: " + lastStartId);
            stopSelf(lastStartId);
        }
    }

    /**
     * Finds the {@link AirshipComponent}s for a given job.
     *
     * @param airship The UAirship instance.
     * @param componentClassName The component's class name.
     * @return The airship component.
     */
    AirshipComponent findAirshipComponent(UAirship airship, String componentClassName) {
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

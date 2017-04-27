/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ResultReceiver;
import android.support.annotation.WorkerThread;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.urbanairship.job.Job;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.util.UAStringUtil;

/**
 * Urban Airship Service.
 *
 * @hide
 */
public class AirshipService extends Service {

    protected static final long AIRSHIP_WAIT_TIME_MS = 10000; // 10 seconds.

    /**
     * Action to run a job.
     */
    public static final String ACTION_RUN_JOB = "RUN_JOB";

    /**
     * Job bundle extra. See {@link Job#toBundle()}.
     */
    public static final String EXTRA_JOB_BUNDLE = "EXTRA_JOB_BUNDLE";

    /**
     * Optional result receiver extra.
     */
    public static final String EXTRA_RESULT_RECEIVER = "EXTRA_RESULT_RECEIVER";

    private static final int MSG_INTENT_RECEIVED = 1;
    private static final int MSG_INTENT_JOB_FINISHED = 2;

    private Looper looper;
    private IncomingHandler handler;
    private int lastStartId = 0;
    private int runningJobs;

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

        Logger.verbose("AirshipService - Received intent: " + intent);
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

        if (intent == null || !ACTION_RUN_JOB.equals(intent.getAction()) || intent.getBundleExtra(EXTRA_JOB_BUNDLE) == null) {
            sendJobResult(intent, msg, Job.JOB_FINISHED);
            return;
        }

        final Job job = Job.fromBundle(intent.getBundleExtra(EXTRA_JOB_BUNDLE));

        Logger.verbose("AirshipService - Starting job: " + job + " taskId: " + startId);

        final UAirship airship = UAirship.waitForTakeOff(AIRSHIP_WAIT_TIME_MS);
        if (airship == null) {
            Logger.error("AirshipService - UAirship not ready. Dropping intent: " + intent);
            handler.sendMessage(msg);
            return;
        }

        final AirshipComponent component = findAirshipComponent(airship, job.getAirshipComponentName());

        if (component == null) {
            Logger.error("AirshipService - Unavailable to find airship components for job with action: " + intent.getAction());
            handler.sendMessage(msg);
            return;
        }

        runningJobs++;
        component.getJobExecutor(job).execute(new Runnable() {
            @Override
            public void run() {
                int result = component.onPerformJob(airship, job);
                Logger.verbose("AirshipService - Job finished: " + job + " with result: " + result);

                if (result == Job.JOB_RETRY) {
                    JobDispatcher.shared(getApplicationContext()).dispatch(job);
                }

                sendJobResult(intent, msg, result);
            }
        });
    }

    /**
     * Helper method to send the results of {@link #onHandleIntent(Intent, int)}
     *
     * @param intent The airship intent.
     * @param msg The internal message.
     * @param result The job result.
     */
    private void sendJobResult(Intent intent, Message msg, @Job.JobResult int result) {
        handler.sendMessage(msg);

        if (intent == null) {
            return;
        }

        ResultReceiver resultReceiver = intent.getParcelableExtra(EXTRA_RESULT_RECEIVER);
        if (resultReceiver != null) {
            resultReceiver.send(result, new Bundle());
        }
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
     * Creates a service intent for the {@link Job}.
     *
     * @param context The application context.
     * @param job The {@link Job} to run.
     * @return A service intent.
     */
    public static Intent createIntent(Context context, Job job) {
        return new Intent(context, AirshipService.class)
                .setAction(AirshipService.ACTION_RUN_JOB)
                .putExtra(AirshipService.EXTRA_JOB_BUNDLE, job.toBundle());
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

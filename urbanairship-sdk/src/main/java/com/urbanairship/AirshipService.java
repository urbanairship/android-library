/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.WorkerThread;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.urbanairship.job.Job;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;

/**
 * Urban Airship Service.
 *
 * @hide
 */
public class AirshipService extends Service {

    /**
     * Action to run a job.
     */
    public static final String ACTION_RUN_JOB = "RUN_JOB";

    /**
     * JobInfo bundle extra. See {@link JobInfo#toBundle()}.
     */
    public static final String EXTRA_JOB_BUNDLE = "EXTRA_JOB_BUNDLE";

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
            handler.sendMessage(msg);
            return;
        }

        final JobInfo jobInfo = JobInfo.fromBundle(intent.getBundleExtra(EXTRA_JOB_BUNDLE));
        Job job = new Job(jobInfo, true);

        Logger.verbose("AirshipService - Starting job: " + job + " taskId: " + startId);
        runningJobs++;

        JobDispatcher.shared(getApplicationContext()).runJob(job, new JobDispatcher.Callback() {
            @Override
            public void onFinish(Job job, @Job.JobResult int result) {
                handler.sendMessage(msg);
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
     * Creates a service intent for the {@link JobInfo}.
     *
     * @param context The application context.
     * @param jobInfo The {@link JobInfo} to run.
     * @return A service intent.
     */
    public static Intent createIntent(Context context, JobInfo jobInfo) {
        return new Intent(context, AirshipService.class)
                .setAction(AirshipService.ACTION_RUN_JOB)
                .putExtra(AirshipService.EXTRA_JOB_BUNDLE, jobInfo.toBundle());
    }

}

/* Copyright Airship and Contributors */

package com.urbanairship.job;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.urbanairship.Logger;
import com.urbanairship.util.AirshipHandlerThread;

/**
 * Airship Service.
 *
 * @hide
 */
public class AirshipService extends Service {

    /**
     * Action to run a job.
     */
    @NonNull
    public static final String ACTION_RUN_JOB = "RUN_JOB";

    /**
     * JobInfo bundle extra. See {@link JobInfo#toBundle()}.
     */
    static final String EXTRA_JOB_INFO_BUNDLE = "EXTRA_JOB_INFO_BUNDLE";

    static final String EXTRA_RESCHEDULE_EXTRAS = "EXTRA_RESCHEDULE_EXTRAS";

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

        HandlerThread thread = new AirshipHandlerThread("Airship Service");
        thread.start();

        looper = thread.getLooper();
        handler = new IncomingHandler(looper);
    }

    @Override
    public int onStartCommand(@Nullable final Intent intent, int flags, final int startId) {
        Message msg = handler.obtainMessage();
        msg.what = MSG_INTENT_RECEIVED;
        msg.arg1 = startId;
        msg.obj = intent;

        Logger.verbose("AirshipService - Received intent: %s", intent);
        handler.sendMessage(msg);

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        looper.quit();
    }

    @Nullable
    @Override
    public IBinder onBind(@Nullable Intent intent) {
        return null;
    }

    @WorkerThread
    private void onHandleIntent(@Nullable final Intent intent, int startId) {
        this.lastStartId = startId;

        final Message msg = handler.obtainMessage();
        msg.what = MSG_INTENT_JOB_FINISHED;
        msg.arg1 = startId;
        msg.obj = intent;

        if (intent == null || !ACTION_RUN_JOB.equals(intent.getAction()) || intent.getBundleExtra(EXTRA_JOB_INFO_BUNDLE) == null) {
            handler.sendMessage(msg);
            return;
        }

        final JobInfo jobInfo = JobInfo.fromBundle(intent.getBundleExtra(EXTRA_JOB_INFO_BUNDLE));
        if (jobInfo == null) {
            handler.sendMessage(msg);
            return;
        }

        runningJobs++;

        Job job = Job.newBuilder(jobInfo)
                     .setCallback(new Job.Callback() {
                         @Override
                         public void onFinish(@NonNull Job job, @JobInfo.JobResult int result) {
                             handler.sendMessage(msg);
                             if (result == JobInfo.JOB_RETRY) {
                                 JobDispatcher.shared(getApplicationContext()).reschedule(jobInfo, intent.getBundleExtra(EXTRA_RESCHEDULE_EXTRAS));
                             }
                         }
                     })
                     .build();

        Logger.verbose("AirshipService - Running job: %s", jobInfo);
        Job.EXECUTOR.execute(job);
    }

    /**
     * Called when a job is finished.
     *
     * @param intent The original intent.
     * @param startId The intent's startId.
     */
    @WorkerThread
    private void onJobFinished(@NonNull Intent intent, int startId) {
        Logger.verbose("AirshipService - Component finished job with startId: %s", startId);

        runningJobs--;

        if (runningJobs <= 0) {
            runningJobs = 0;
            Logger.verbose("AirshipService - All jobs finished, stopping with last startId: %s", lastStartId);
            stopSelf(lastStartId);
        }
    }

    /**
     * Creates a service intent for the {@link JobInfo}.
     *
     * @param context The application context.
     * @param jobInfo The {@link JobInfo} to run.
     * @param rescheduleExtras Extras to pass to {@link JobDispatcher#reschedule(JobInfo, Bundle)} if the job needs to be retried.
     * @return A service intent.
     */
    @NonNull
    public static Intent createIntent(@NonNull Context context, @Nullable JobInfo jobInfo, @Nullable Bundle rescheduleExtras) {
        Intent intent = new Intent(context, AirshipService.class)
                .setAction(AirshipService.ACTION_RUN_JOB);

        if (jobInfo != null) {
            intent.putExtra(AirshipService.EXTRA_JOB_INFO_BUNDLE, jobInfo.toBundle());
        }

        if (rescheduleExtras != null) {
            intent.putExtra(AirshipService.EXTRA_RESCHEDULE_EXTRAS, rescheduleExtras);
        }

        return intent;
    }

}

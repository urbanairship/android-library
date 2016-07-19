/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.SparseIntArray;


/**
 * Urban Airship Service.
 */
public class AirshipService extends Service {

    /**
     * Intent extra to track the backoff delay.
     */
    private static final String EXTRA_BACK_OFF_MS = "com.urbanairship.EXTRA_BACK_OFF_MS";

    /**
     * The default starting back off time for retries in milliseconds
     */
    protected static final long DEFAULT_STARTING_BACK_OFF_TIME_MS = 10000; // 10 seconds.

    /**
     * The default max back off time for retries in milliseconds.
     */
    protected static final long DEFAULT_MAX_BACK_OFF_TIME_MS = 5120000; // About 85 mins.

    private static final int MSG_INTENT_RECEIVED = 1;
    private static final int MSG_INTENT_TASK_FINISHED = 2;

    private volatile Looper looper;
    private volatile IncomingHandler handler;
    private int lastStartId = 0;
    private final SparseIntArray runningTasks = new SparseIntArray();

    private final class IncomingHandler extends Handler {
        public IncomingHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_INTENT_RECEIVED:
                    onStartIntentTasks((Intent)msg.obj, msg.arg1);
                    break;

                case MSG_INTENT_TASK_FINISHED:
                    onTaskFinished((Intent)msg.obj, msg.arg1);
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
    private void onStartIntentTasks(final Intent intent, int startId) {
        Logger.debug("AirshipService - Starting tasks for intent: " + intent + " taskId: " + startId);

        this.lastStartId = startId;

        final Message msg = handler.obtainMessage();
        msg.what = MSG_INTENT_TASK_FINISHED;
        msg.arg1 = startId;
        msg.obj = intent;

        String action = intent.getAction();
        if (action == null) {
            handler.sendMessage(msg);
            return;
        }

        final UAirship airship = UAirship.waitForTakeOff();
        if (airship == null) {
            Logger.error("Airship unavailable, dropping intent: " + intent);
            handler.sendMessage(msg);
            return;
        }

        int taskCount = 0;

        for (final AirshipComponent component : airship.getComponents()) {
            if (component.acceptsIntentAction(airship, intent.getAction())) {
                taskCount ++;
                component.serviceExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        component.onHandleIntent(airship, intent);
                        handler.sendMessage(msg);
                    }
                });
            }
        }

        if (taskCount == 0) {
            handler.handleMessage(msg);
        } else {
            runningTasks.put(startId, taskCount);
        }
    }

    @WorkerThread
    private void onTaskFinished(Intent intent, int taskId) {
        Logger.verbose("AirshipService - Task finished for id: " + taskId);

        int taskCount = runningTasks.get(taskId, 0) - 1;

        if (taskCount > 0) {
            runningTasks.put(taskId, taskCount);
        } else {
            Logger.verbose("AirshipService - No more running tasks for id: " + taskId);

            runningTasks.delete(taskId);
            WakefulBroadcastReceiver.completeWakefulIntent(intent);
        }

        if (runningTasks.size() == 0) {
            Logger.verbose("AirshipService - All tasks finished, stopping with last start ID: " + lastStartId);
            stopSelf(lastStartId);
        }
    }

    /**
     * Schedules the intent to be retried with exponential backoff.
     * </p>
     * Retries work by adding {@link #EXTRA_BACK_OFF_MS} to the intent to track the backoff.
     * This makes retries not work if its called with a new intent or an intent with its extras cleared.
     *
     * @param context The application context.
     * @param intent The intent to retry.
     */
    public static void retryServiceIntent(Context context, @NonNull Intent intent) {
        retryServiceIntent(context, intent, DEFAULT_STARTING_BACK_OFF_TIME_MS, DEFAULT_MAX_BACK_OFF_TIME_MS);
    }

    /**
     * Schedules the intent to be retried with exponential backoff.
     * </p>
     * Retries work by adding {@link #EXTRA_BACK_OFF_MS} to the intent to track the backoff.
     * This makes retries not work if its called with a new intent or an intent with its extras cleared.
     *
     * @param context The application context.
     * @param intent The intent to retry.
     * @param initialDelay The initial delay.
     * @param maxDelay The max delay.
     */
    public static void retryServiceIntent(Context context, @NonNull Intent intent, long initialDelay, long maxDelay) {
        // Copy it so we don't modify the original intent
        intent = new Intent(intent);

        // Remove the wakeful broadcast extra so it does not log a warning that it already
        // handled the wake lock. Since this value is private, we have to hard code it. In the
        // unlikely case that this value changes in the future it will only cause any wakeful
        // intents to log a warning.
        intent.removeExtra("android.support.content.wakelockid");

        // Calculate the backoff
        long delay = intent.getLongExtra(EXTRA_BACK_OFF_MS, 0);
        if (delay <= 0) {
            delay = initialDelay;
        } else {
            delay = Math.min(delay * 2, maxDelay);
        }

        // Store the backoff in the intent
        intent.putExtra(EXTRA_BACK_OFF_MS, delay);

        // Schedule the intent
        Logger.verbose("AirshipService - Scheduling intent " + intent.getAction() + " in " + delay + " milliseconds.");
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        try {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + delay, pendingIntent);
        } catch (SecurityException e) {
            Logger.error("AirshipService - Failed to schedule intent " + intent.getAction(), e);
        }
    }
}

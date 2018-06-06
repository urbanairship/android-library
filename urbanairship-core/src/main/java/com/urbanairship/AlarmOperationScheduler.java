/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.RequiresApi;
import android.support.annotation.RestrictTo;
import android.util.SparseArray;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Alarm scheduler.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AlarmOperationScheduler implements OperationScheduler {

    private final Context context;
    private InternalScheduler scheduler;
    @SuppressLint("StaticFieldLeak")
    private static AlarmOperationScheduler shared;

    /**
     * Default constructor.
     *
     * @param context The application context.
     */
    AlarmOperationScheduler(Context context) {
        this.context = context.getApplicationContext();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.scheduler = new NougatScheduler();
        } else {
            this.scheduler = new JellyBeanScheduler();
        }
    }

    /**
     * Gets the shared instance of the alarm scheduler.
     *
     * @param context The application context.
     * @return The shared alarm scheduler instance.
     */
    public static AlarmOperationScheduler shared(Context context) {
        synchronized (AlarmOperationScheduler.class) {
            if (shared == null) {
                shared = new AlarmOperationScheduler(context);
            }
        }

        return shared;
    }

    @Override
    public void schedule(long delay, final CancelableOperation operation) {
        scheduler.schedule(context, delay, operation);
    }

    /**
     * Scheduler interface.
     */
    interface InternalScheduler {

        /**
         * Schedules an operation.
         *
         * @param context The context.
         * @param delay The delay in milliseconds.
         * @param operation The operation.
         */
        void schedule(Context context, long delay, CancelableOperation operation);
    }

    /**
     * Jelly bean+ scheduler.
     */
    private static class JellyBeanScheduler implements InternalScheduler {

        private static final String ACTION = "com.urbanairship.alarmhelper";
        private static final String ID_EXRA = "ID";

        private final SparseArray<CancelableOperation> operations = new SparseArray<>();
        private final AtomicInteger count = new AtomicInteger();
        private boolean isRegistered = false;

        private final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || !ACTION.equals(intent.getAction())) {
                    return;
                }

                int operationId = intent.getIntExtra(ID_EXRA, -1);
                operations.get(operationId).run();
                operations.remove(operationId);
            }
        };

        @Override
        public void schedule(Context context, long delay, CancelableOperation operation) {
            synchronized (receiver) {
                if (!isRegistered) {
                    IntentFilter intentFilter = new IntentFilter();
                    intentFilter.addAction(ACTION);
                    intentFilter.addCategory(toString());

                    context.registerReceiver(receiver, intentFilter, null, null);
                    isRegistered = true;
                }
            }

            final int operationId = count.getAndIncrement();

            final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(ACTION)
                    .putExtra(ID_EXRA, operationId)
                    .addCategory(toString());

            final PendingIntent pendingIntent = PendingIntent.getBroadcast(context, operationId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + delay, pendingIntent);

            operation.addOnCancel(new CancelableOperation() {
                @Override
                protected void onCancel() {
                    alarmManager.cancel(pendingIntent);
                }
            });

            operations.append(operationId, operation);
        }
    }

    /**
     * Nougat+ scheduler.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private static class NougatScheduler implements InternalScheduler {

        @Override
        public void schedule(Context context, long delay, CancelableOperation operation) {
            final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            AlarmListener listener = new AlarmListener(alarmManager, operation);
            alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + delay, UAirship.getPackageName(), listener, operation.getHandler());
            operation.addOnCancel(listener);
        }


        static class AlarmListener extends CancelableOperation implements AlarmManager.OnAlarmListener {

            private final AlarmManager alarmManager;
            private final Runnable runnable;

            public AlarmListener(AlarmManager alarmManager, Runnable runnable) {
                this.alarmManager = alarmManager;
                this.runnable = runnable;
            }

            @Override
            public void onAlarm() {
                onRun();
            }

            @Override
            protected void onRun() {
                runnable.run();
            }

            @Override
            protected void onCancel() {
                alarmManager.cancel(this);
            }
        }
    }
}

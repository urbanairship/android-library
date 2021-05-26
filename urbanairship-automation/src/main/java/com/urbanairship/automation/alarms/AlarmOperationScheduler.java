/* Copyright Airship and Contributors */

package com.urbanairship.automation.alarms;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.urbanairship.Logger;
import com.urbanairship.util.Clock;
import com.urbanairship.util.PendingIntentCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

/**
 * Alarm scheduler.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AlarmOperationScheduler implements OperationScheduler {

    interface AlarmManagerDelegate {
        void onSchedule(long realTimeMilliseconds, @NonNull PendingIntent pendingIntent);
    }

    private static class PendingOperation {
        final Runnable operation;
        final long scheduledTime;

        PendingOperation(long scheduledTime, @NonNull Runnable operation) {
            this.operation = operation;
            this.scheduledTime = scheduledTime;
        }
    }

    private final Comparator<PendingOperation> OPERATION_COMPARATOR = new Comparator<PendingOperation>() {
        @Override
        public int compare(PendingOperation lhs, PendingOperation rhs) {
            return Long.valueOf(lhs.scheduledTime).compareTo(rhs.scheduledTime);
        }
    };

    private final List<PendingOperation> pendingOperations = new ArrayList<>();
    private final Clock clock;
    private final AlarmManagerDelegate delegate;
    private final Context context;

    @SuppressLint("StaticFieldLeak")
    private static AlarmOperationScheduler shared;

    /**
     * Default constructor.
     *
     * @param context The application context.
     */
    AlarmOperationScheduler(@NonNull final Context context) {
        this(context, Clock.DEFAULT_CLOCK, new AlarmManagerDelegate() {
            @Override
            public void onSchedule(long realTimeMilliseconds, @NonNull PendingIntent pendingIntent) {
                final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                if (alarmManager == null) {
                    throw new IllegalStateException("AlarmManager unavailable");
                }
                alarmManager.set(AlarmManager.ELAPSED_REALTIME, realTimeMilliseconds, pendingIntent);
            }
        });
    }

    @VisibleForTesting
    AlarmOperationScheduler(@NonNull Context context, @NonNull Clock clock, @NonNull AlarmManagerDelegate delegate) {
        this.context = context;
        this.clock = clock;
        this.delegate = delegate;
    }

    /**
     * Gets the shared instance of the alarm scheduler.
     *
     * @param context The application context.
     * @return The shared alarm scheduler instance.
     */
    @NonNull
    public static AlarmOperationScheduler shared(@NonNull Context context) {
        synchronized (AlarmOperationScheduler.class) {
            if (shared == null) {
                shared = new AlarmOperationScheduler(context.getApplicationContext());
            }
        }

        return shared;
    }

    @Override
    public void schedule(long delay, @NonNull final Runnable operation) {
        long time = clock.elapsedRealtime() + delay;
        PendingOperation pendingOperation = new PendingOperation(time, operation);

        Logger.verbose("Operation scheduled with %d delay", delay);

        synchronized (pendingOperations) {
            pendingOperations.add(pendingOperation);
            Collections.sort(pendingOperations, OPERATION_COMPARATOR);
            scheduleAlarm();
        }
    }

    /**
     * Called by {@link AlarmOperationReceiver}.
     */
    void onAlarmFired() {
        Logger.verbose("Alarm fired");

        long time = clock.elapsedRealtime();

        synchronized (pendingOperations) {
            List<PendingOperation> copy = new ArrayList<>(pendingOperations);
            for (PendingOperation pendingOperation : copy) {
                if (pendingOperation.scheduledTime <= time) {
                    pendingOperation.operation.run();
                    pendingOperations.remove(pendingOperation);
                }
            }

            scheduleAlarm();
        }
    }

    private void scheduleAlarm() {
        long nextScheduleTime;
        synchronized (pendingOperations) {
            if (pendingOperations.isEmpty()) {
                return;
            }

            nextScheduleTime = pendingOperations.get(0).scheduledTime;
        }

        Intent intent = new Intent(context, AlarmOperationReceiver.class).setAction(AlarmOperationReceiver.ACTION);
        PendingIntent pendingIntent = PendingIntentCompat.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        try {
            delegate.onSchedule(nextScheduleTime, pendingIntent);
            Logger.verbose("Next alarm set %d", nextScheduleTime - clock.elapsedRealtime());
        } catch (Exception e) {
            Logger.error(e, "AlarmOperationScheduler - Failed to schedule alarm.");
        }
    }

}

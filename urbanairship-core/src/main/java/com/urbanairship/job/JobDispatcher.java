/* Copyright Airship and Contributors */

package com.urbanairship.job;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.urbanairship.Logger;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

/**
 * Dispatches jobs.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class JobDispatcher {

    private final Context context;

    @SuppressLint("StaticFieldLeak")
    private static JobDispatcher instance;

    private final Scheduler scheduler;
    private final List<JobInfo> pendingJobInfos = new ArrayList<>();

    private static final long RETRY_DELAY_MS = 1000;

    private final Runnable retryPendingRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                dispatchPending();
            } catch (SchedulerException e) {
                schedulePending();
            }
        }
    };

    /**
     * Gets the shared instance.
     *
     * @param context The application context.
     * @return The JobDispatcher.
     */
    @NonNull
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
    public static void setInstance(@NonNull JobDispatcher dispatcher) {
        synchronized (JobDispatcher.class) {
            instance = dispatcher;
        }
    }

    private JobDispatcher(@NonNull Context context) {
        this(context, new WorkManagerScheduler());
    }

    @VisibleForTesting
    public JobDispatcher(@NonNull Context context, Scheduler scheduler) {
        this.context = context.getApplicationContext();
        this.scheduler = scheduler;
    }

    /**
     * Dispatches a jobInfo to be performed immediately.
     *
     * @param jobInfo The jobInfo.
     */
    public void dispatch(@NonNull JobInfo jobInfo) {
        try {
            dispatchPending();
            scheduler.schedule(context, jobInfo);
        } catch (SchedulerException e) {
            Logger.error(e, "Scheduler failed to schedule jobInfo");
            synchronized (pendingJobInfos) {
                pendingJobInfos.add(jobInfo);
            }
            schedulePending();
        }
    }

    private void schedulePending() {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.removeCallbacks(retryPendingRunnable);
        handler.postDelayed(retryPendingRunnable, RETRY_DELAY_MS);
    }

    private void dispatchPending() throws SchedulerException {
        synchronized (pendingJobInfos) {
            for (JobInfo info : new ArrayList<>(pendingJobInfos)) {
                scheduler.schedule(context, info);
                pendingJobInfos.remove(info);
            }
        }
    }

}

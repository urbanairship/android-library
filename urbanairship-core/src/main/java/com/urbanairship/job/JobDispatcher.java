/* Copyright Airship and Contributors */

package com.urbanairship.job;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.urbanairship.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Consumer;

/**
 * Dispatches jobs.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class JobDispatcher {

    private static final int RESCHEDULE_RETRY_COUNT = 5;
    static final long RESCHEDULE_RETRY_DELAY_MS = TimeUnit.HOURS.toMillis(1);
    private static final long RETRY_DELAY_MS = 1000;

    @SuppressLint("StaticFieldLeak")
    private static JobDispatcher instance;

    private final Context context;
    private final JobRunner jobRunner;
    private final RateLimiter rateLimiter;
    private final Scheduler scheduler;

    private final List<Pending> pendingJobInfos = new ArrayList<>();
    private final Runnable retryPendingRunnable = () -> {
        try {
            dispatchPending();
        } catch (SchedulerException e) {
            schedulePending();
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
    public JobDispatcher(@NonNull Context context,
                         @NonNull Scheduler scheduler) {
        this(context, scheduler, new JobRunner.DefaultRunner(), new RateLimiter());
    }

    @VisibleForTesting
    public JobDispatcher(@NonNull Context context,
                         @NonNull Scheduler scheduler,
                         @NonNull JobRunner jobRunner,
                         @NonNull RateLimiter rateLimiter) {
        this.context = context.getApplicationContext();
        this.scheduler = scheduler;
        this.jobRunner = jobRunner;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Sets a rate limit.
     *
     * @param limitId Limit Id.
     * @param rate The number of events for the duration.
     * @param duration The duration.
     * @param durationUnit The duration unit.
     */
    public void setRateLimit(@NonNull String limitId, @IntRange(from = 1) int rate, long duration, @NonNull TimeUnit durationUnit) {
        this.rateLimiter.setLimit(limitId, rate, duration, durationUnit);
    }

    /**
     * Dispatches a jobInfo to be performed immediately.
     *
     * @param jobInfo The jobInfo.
     */
    public void dispatch(@NonNull JobInfo jobInfo) {
       dispatch(jobInfo, getDelay(jobInfo));
    }

    private void dispatch(@NonNull JobInfo jobInfo, long delayMs) {
        try {
            dispatchPending();
            scheduler.schedule(context, jobInfo, delayMs);
        } catch (SchedulerException e) {
            Logger.error(e, "Scheduler failed to schedule jobInfo");
            synchronized (pendingJobInfos) {
                pendingJobInfos.add(new Pending(jobInfo, delayMs));
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
            for (Pending pending : new ArrayList<>(pendingJobInfos)) {
                scheduler.schedule(context, pending.jobInfo, pending.delayMs);
                pendingJobInfos.remove(pending);
            }
        }
    }

    protected void onStartJob(@NonNull JobInfo jobInfo, long runAttempt, @NonNull Consumer<JobResult> callback) {
        Logger.verbose("Running job: %s, run attempt: %s", jobInfo, runAttempt);

        long rateLimitDelay = getRateLimitDelay(jobInfo);
        if (rateLimitDelay > 0) {
            callback.accept(JobResult.FAILURE);
            dispatch(jobInfo, rateLimitDelay);
            return;
        }

        for (String rateLimitID : jobInfo.getRateLimitIds()) {
            rateLimiter.track(rateLimitID);
        }

        jobRunner.run(jobInfo, (result) -> {
            Logger.verbose("Job finished. Job info: %s, result: %s", jobInfo, result);
            if (result == JobResult.RETRY && runAttempt >= RESCHEDULE_RETRY_COUNT) {
                Logger.verbose("Job retry limit reached. Rescheduling for a later time. Job info: %s, work Id: %s", jobInfo);
                dispatch(jobInfo, RESCHEDULE_RETRY_DELAY_MS);
                callback.accept(JobResult.FAILURE);
            } else {
                callback.accept(result);
            }
        });
    }

    private long getDelay(@NonNull JobInfo jobInfo) {
        return Math.max(jobInfo.getMinDelayMs(), getRateLimitDelay(jobInfo));
    }

    private long getRateLimitDelay(@NonNull JobInfo jobInfo) {
        long delay = 0;

        for (String rateLimitId : jobInfo.getRateLimitIds()) {
            RateLimiter.Status status = rateLimiter.status(rateLimitId);
            if (status != null && status.getLimitStatus() == RateLimiter.LimitStatus.OVER) {
                delay = Math.max(delay, status.getNextAvailable(TimeUnit.MILLISECONDS));
            }
        }

        return delay;
    }

    private static class Pending {
        @NonNull
        private final JobInfo jobInfo;
        private final long delayMs;
        Pending(@NonNull JobInfo jobInfo, long delayMs) {
            this.jobInfo = jobInfo;
            this.delayMs = delayMs;
        }
    }
}

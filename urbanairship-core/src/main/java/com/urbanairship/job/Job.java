package com.urbanairship.job;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.WorkerThread;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipExecutors;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.util.UAStringUtil;

import java.util.concurrent.Executor;

/**
 * Contains information for a job run.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class Job implements Runnable {

    static final Executor EXECUTOR = AirshipExecutors.newSerialExecutor();

    private static final long AIRSHIP_WAIT_TIME_MS = 5000; // 5 seconds.

    /**
     * Callback when a job is finished.
     */
    public interface Callback {

        /**
         * Called when a job is finished.
         *
         * @param job The job.
         * @param result The job's result.
         */
        void onFinish(@NonNull Job job, @JobInfo.JobResult int result);

    }

    private final JobInfo jobInfo;
    private final Callback callback;

    /**
     * Default constructor.
     *
     * @param builder The job builder.
     */
    private Job(@NonNull Builder builder) {
        this.jobInfo = builder.jobInfo;
        this.callback = builder.callback;
    }

    /**
     * Runs the job.
     */
    @Override
    @WorkerThread
    public void run() {
        final UAirship airship = UAirship.waitForTakeOff(AIRSHIP_WAIT_TIME_MS);
        if (airship == null) {
            Logger.error("JobDispatcher - UAirship not ready. Rescheduling job: %s", jobInfo);
            if (callback != null) {
                callback.onFinish(this, JobInfo.JOB_RETRY);
            }
            return;
        }

        final AirshipComponent component = findAirshipComponent(airship, jobInfo.getAirshipComponentName());
        if (component == null) {
            Logger.error("JobDispatcher - Unavailable to find airship components for jobInfo: %s", jobInfo);
            if (callback != null) {
                callback.onFinish(this, JobInfo.JOB_FINISHED);
            }
            return;
        }

        if (!component.isComponentEnabled()) {
            Logger.debug("JobDispatcher - Component disabled. Dropping jobInfo: %s", jobInfo);
            if (callback != null) {
                callback.onFinish(this, JobInfo.JOB_FINISHED);
            }
            return;
        }

        component.getJobExecutor(jobInfo).execute(new Runnable() {
            @Override
            public void run() {
                int result = component.onPerformJob(airship, jobInfo);
                Logger.verbose("Job - Finished: %s with result: %s", jobInfo, result);

                if (callback != null) {
                    callback.onFinish(Job.this, result);
                }
            }
        });
    }

    /**
     * Finds the {@link AirshipComponent}s for a given job.
     *
     * @param componentClassName The component's class name.
     * @param airship The airship instance.
     * @return The airship component.
     */
    private AirshipComponent findAirshipComponent(@NonNull UAirship airship, String componentClassName) {
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

    /**
     * Creates a new builder from job info.
     *
     * @param jobInfo The job info.
     * @return A new job builder.
     */
    @NonNull
    public static Builder newBuilder(@NonNull JobInfo jobInfo) {
        return new Builder(jobInfo);
    }

    /**
     * Builds the job
     */
    public static class Builder {

        private final JobInfo jobInfo;
        private Callback callback;

        /**
         * Default constructor.
         *
         * @param jobInfo The job info.
         */
        Builder(@NonNull JobInfo jobInfo) {
            this.jobInfo = jobInfo;
        }

        /**
         * Sets a callback when the job is finished.
         *
         * @param callback A callback.
         * @return The builder instance.
         */
        @NonNull
        Builder setCallback(@NonNull Callback callback) {
            this.callback = callback;
            return this;
        }

        /**
         * Builds the job.
         *
         * @return The job.
         */
        @NonNull
        Job build() {
            return new Job(this);
        }

    }

}

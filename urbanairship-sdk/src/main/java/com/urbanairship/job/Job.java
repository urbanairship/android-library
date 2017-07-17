package com.urbanairship.job;

import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;
import android.support.annotation.WorkerThread;

import com.urbanairship.AirshipComponent;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.util.UAStringUtil;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


/**
 * Contains information for a job run.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class Job implements Runnable {

    static final Executor EXECUTOR = Executors.newSingleThreadExecutor();

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
        void onFinish(Job job, @JobInfo.JobResult int result);
    }

    private final JobInfo jobInfo;
    private final Callback callback;


    /**
     * Default constructor.
     *
     * @param builder The job builder.
     */
    private Job(Builder builder) {
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
            Logger.error("JobDispatcher - UAirship not ready. Rescheduling job: " + jobInfo);
            if (callback != null) {
                callback.onFinish(this, JobInfo.JOB_RETRY);
            }
            return;
        }

        final AirshipComponent component = findAirshipComponent(airship, jobInfo.getAirshipComponentName());
        if (component == null) {
            Logger.error("JobDispatcher - Unavailable to find airship components for jobInfo: " + jobInfo);
            if (callback != null) {
                callback.onFinish(this, JobInfo.JOB_FINISHED);
            }
            return;
        }

        component.getJobExecutor(jobInfo).execute(new Runnable() {
            @Override
            public void run() {
                int result = component.onPerformJob(airship, jobInfo);
                Logger.verbose("Job - Finished: " + jobInfo + " with result: " + result);

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


    /**
     * Builds the job
     */
    public static class Builder {

        private JobInfo jobInfo;
        private Callback callback;

        /**
         * Default constructor.
         *
         * @param jobInfo The job info.
         */
        Builder(JobInfo jobInfo) {
            this.jobInfo = jobInfo;
        }

        /**
         * Sets a callback when the job is finished.
         *
         * @param callback A callback.
         * @return The builder instance.
         */
        Builder setCallback(@NonNull Callback callback) {
            this.callback = callback;
            return this;
        }

        /**
         * Builds the job.
         *
         * @return The job.
         */
        Job build() {
            return new Job(this);
        }

    }


}

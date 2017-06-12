/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;
import android.support.annotation.WorkerThread;

import com.urbanairship.job.Job;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Base class for Urban Airship components.
 */
public abstract class AirshipComponent {

    /**
     * Default job executor.
     */
    private Executor jobExecutor = Executors.newSingleThreadExecutor();

    /**
     * Initialize the manager.
     * Called in {@link UAirship} during takeoff.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected void init() {}

    /**
     * Tear down the manager.
     * Called in {@link UAirship} during land.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected void tearDown() {}

    /**
     * Gets the executor for the given job.
     *
     * @param job The job.
     * @return An executor that will be used to call {@link #onPerformJob(UAirship, Job)}.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public Executor getJobExecutor(Job job) {
        return jobExecutor;
    }

    /**
     * Called when a scheduled {@link Job} is ready to perform.
     *
     * @param airship The airship instance.
     * @param job The scheduled job.
     * @return The result of the job.
     * @hide
     */
    @WorkerThread
    @Job.JobResult
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public int onPerformJob(@NonNull UAirship airship, Job job) {
        return Job.JOB_FINISHED;
    }

}

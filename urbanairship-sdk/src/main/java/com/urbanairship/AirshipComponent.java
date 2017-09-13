/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;
import android.support.annotation.WorkerThread;

import com.urbanairship.job.JobInfo;

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
     * @param jobInfo The jobInfo.
     * @return An executor that will be used to call {@link #onPerformJob(UAirship, JobInfo)}.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public Executor getJobExecutor(JobInfo jobInfo) {
        return jobExecutor;
    }

    /**
     * Called when a scheduled {@link JobInfo} is ready to perform.
     *
     * @param airship The airship instance.
     * @param jobInfo The JobInfo.
     * @return The result of the jobInfo.
     * @hide
     */
    @WorkerThread
    @JobInfo.JobResult
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public int onPerformJob(@NonNull UAirship airship, JobInfo jobInfo) {
        return JobInfo.JOB_FINISHED;
    }

}

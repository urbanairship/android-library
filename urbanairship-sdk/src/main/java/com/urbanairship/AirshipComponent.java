/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import com.urbanairship.job.Job;

/**
 * Base class for Urban Airship components.
 */
public abstract class AirshipComponent {

    /**
     * Initialize the manager.
     * Called in {@link UAirship} during takeoff.
     *
     * @hide
     */
    protected void init() {}

    /**
     * Tear down the manager.
     * Called in {@link UAirship} during land.
     *
     * @hide
     */
    protected void tearDown() {}

    /**
     * Called when a scheduled {@link Job} is ready to perform.
     *
     * @param airship The airship instance.
     * @param job The scheduled job.
     * @return The result of the job.
     *
     * @hide
     */
    @WorkerThread
    protected @Job.JobResult int onPerformJob(@NonNull UAirship airship, Job job) {
        return Job.JOB_FINISHED;
    }
}

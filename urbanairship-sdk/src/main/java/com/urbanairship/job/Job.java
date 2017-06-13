package com.urbanairship.job;

import android.support.annotation.IntDef;
import android.support.annotation.RestrictTo;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


/**
 * Contains information for a job run.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Job {


    @IntDef({ JOB_FINISHED, JOB_RETRY })
    @Retention(RetentionPolicy.SOURCE)
    public @interface JobResult {}

    /**
     * JobInfo is finished.
     */
    public static final int JOB_FINISHED = 0;

    /**
     * JobInfo needs to be retried at a later date.
     */
    public static final int JOB_RETRY = 1;


    private final boolean isLongRunning;
    private final JobInfo jobInfo;

    /**
     * The default job constructor.
     *
     * @param jobInfo The job info.
     * @param isLongRunning {@code true} if the job is long running, otherwise {@code false}.
     */
    public Job(JobInfo jobInfo, boolean isLongRunning) {
        this.jobInfo = jobInfo;
        this.isLongRunning = isLongRunning;
    }

    @Override
    public String toString() {
        return "Job{" +
                "isLongRunning=" + isLongRunning +
                ", jobInfo=" + jobInfo +
                '}';
    }

    /**
     * Gets the job info.
     *
     * @return The job info.
     */
    public JobInfo getJobInfo() {
        return jobInfo;
    }

    /**
     * If the job is long running or not. If its not long running, it has less than 10 seconds to run.
     *
     * @return {@code true} if the job is long running, otherwise {@code false}.
     */
    public boolean isLongRunning() {
        return isLongRunning;
    }
}

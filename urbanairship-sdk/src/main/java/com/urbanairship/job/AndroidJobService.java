package com.urbanairship.job;

import android.app.job.JobParameters;
import android.app.job.JobService;

import com.urbanairship.Logger;


/**
 * Android Job Service.
 *
 * @hide
 */
public class AndroidJobService extends JobService {

    @Override
    public boolean onStartJob(final JobParameters jobParameters) {
        JobInfo jobInfo = JobInfo.fromPersistableBundle(jobParameters.getExtras());

        if (jobInfo == null) {
            Logger.error("AndroidJobService: Failed to parse jobInfo.");
            return false;
        }

        Job job = new Job.Builder(jobInfo)
                .setCallback(new Job.Callback() {
                    @Override
                    public void onFinish(Job job, @JobInfo.JobResult int result) {
                        jobFinished(jobParameters, result == JobInfo.JOB_RETRY);
                    }
                })
                .build();

        Logger.verbose("AndroidJobService - Running job: " + jobInfo);

        Job.EXECUTOR.execute(job);

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        return false;
    }
}

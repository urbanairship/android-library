package com.urbanairship.job;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.urbanairship.Logger;

/**
 * Android Job Service.
 *
 * @hide
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
public class AndroidJobService extends JobService {

    @Override
    public boolean onStartJob(@NonNull final JobParameters jobParameters) {
        JobInfo jobInfo = JobInfo.fromPersistableBundle(jobParameters.getExtras());

        if (jobInfo == null) {
            Logger.error("AndroidJobService: Failed to parse jobInfo.");
            return false;
        }

        Job job = Job.newBuilder(jobInfo)
                     .setCallback(new Job.Callback() {
                         @Override
                         public void onFinish(@NonNull Job job, @JobInfo.JobResult int result) {
                             jobFinished(jobParameters, result == JobInfo.JOB_RETRY);
                         }
                     })
                     .build();

        Logger.verbose("AndroidJobService - Running job: %s", jobInfo);

        Job.EXECUTOR.execute(job);

        return true;
    }

    @Override
    public boolean onStopJob(@NonNull JobParameters jobParameters) {
        return false;
    }

}

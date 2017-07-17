/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.job;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.TaskParams;
import com.urbanairship.Logger;
import com.urbanairship.google.PlayServicesUtils;

import java.util.concurrent.CountDownLatch;

/**
 * GcmTaskService task service. Forwards jobs to the {@link AirshipService}.
 *
 * @hide
 */
public class AirshipGcmTaskService extends com.google.android.gms.gcm.GcmTaskService {

    @Override
    public int onRunTask(TaskParams taskParams) {
        if (!PlayServicesUtils.isGooglePlayStoreAvailable(getApplicationContext())) {
            Logger.error("AirshipGcmTaskService: Google play services is unavailable. Ignoring jobInfo requests.");
            return GcmNetworkManager.RESULT_FAILURE;
        }

        JobInfo jobInfo = JobInfo.fromBundle(taskParams.getExtras());

        if (jobInfo == null) {
            Logger.error("AirshipGcmTaskService: Failed to parse jobInfo.");
            return GcmNetworkManager.RESULT_FAILURE;
        }

        final CountDownLatch latch = new CountDownLatch(1);

        JobCallback callback = new JobCallback() {
            @Override
            public void onFinish(Job job, @JobInfo.JobResult int result) {
                super.onFinish(job, result);
                latch.countDown();
            }
        };

        Job job = new Job.Builder(jobInfo)
                .setCallback(callback)
                .build();

        Logger.verbose("AirshipGcmTaskService - Running job: " + jobInfo);

        Job.EXECUTOR.execute(job);

        try {
            Logger.verbose("AirshipGcmTaskService - Waiting for jobInfo: " + jobInfo + " to complete.");
            latch.await();
        } catch (InterruptedException e) {
            Logger.error("Failed to wait for task: " + taskParams);
            return GcmNetworkManager.RESULT_FAILURE;
        }

        if (callback.resultCode == JobInfo.JOB_RETRY) {
            Logger.verbose("AirshipGcmTaskService - Rescheduling jobInfo " + jobInfo);
            return GcmNetworkManager.RESULT_RESCHEDULE;
        } else {
            Logger.verbose("AirshipGcmTaskService - JobInfo finished: " + jobInfo);
            return GcmNetworkManager.RESULT_SUCCESS;
        }
    }

    /**
     * JobDispatcher.Callback that captures the result as a field.
     */
    private static class JobCallback implements Job.Callback {
        int resultCode;

        @Override
        public void onFinish(Job job, @JobInfo.JobResult int result) {
            this.resultCode = result;
        }
    }
}

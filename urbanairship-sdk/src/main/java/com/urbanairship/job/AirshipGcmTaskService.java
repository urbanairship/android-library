/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.job;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.TaskParams;
import com.urbanairship.AirshipService;
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
            Logger.error("AirshipGcmTaskService: Google play services is unavailable. Ignoring job requests.");
            return GcmNetworkManager.RESULT_FAILURE;
        }

        Job job = Job.fromBundle(taskParams.getExtras());

        if (job == null) {
            Logger.error("AirshipGcmTaskService: Failed to parse job.");
            return GcmNetworkManager.RESULT_FAILURE;
        }

        final CountDownLatch latch = new CountDownLatch(1);

        JobCallback callback = new JobCallback() {
            @Override
            public void onFinish(Job job, @Job.JobResult int result) {
                super.onFinish(job, result);
                latch.countDown();
            }
        };

        JobDispatcher.shared(getApplicationContext()).runJob(job, callback);

        try {
            Logger.verbose("AirshipGcmTaskService - Waiting for job: " + job + " to complete.");
            latch.await();
        } catch (InterruptedException e) {
            Logger.error("Failed to wait for task: " + taskParams);
            return GcmNetworkManager.RESULT_FAILURE;
        }

        if (callback.resultCode == Job.JOB_RETRY) {
            Logger.verbose("AirshipGcmTaskService - Rescheduling job " + job);
            return GcmNetworkManager.RESULT_RESCHEDULE;
        } else {
            Logger.verbose("AirshipGcmTaskService - Job finished: " + job);
            return GcmNetworkManager.RESULT_SUCCESS;
        }
    }

    /**
     * JobDispatcher.Callback that captures the result as a field.
     */
    private static class JobCallback implements JobDispatcher.Callback {
        int resultCode;

        @Override
        public void onFinish(Job job, @Job.JobResult int result) {
            this.resultCode = result;
        }
    }
}

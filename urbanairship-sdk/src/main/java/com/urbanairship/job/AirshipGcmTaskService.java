/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.job;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;

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
            Logger.error("Google play services is unavailable. Ignoring job requests.");
            return GcmNetworkManager.RESULT_FAILURE;
        }

        final CountDownLatch latch = new CountDownLatch(1);

        JobResultReceiver resultReceiver = new JobResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                super.onReceiveResult(resultCode, resultData);
                latch.countDown();
            }
        };

        Job job = Job.fromBundle(taskParams.getExtras());

        Logger.verbose("AirshipGcmTaskService - Starting AirshipService for job: " + job);

        Intent intent = AirshipService.createIntent(getApplicationContext(), job)
                                      .putExtra(AirshipService.EXTRA_RESULT_RECEIVER, resultReceiver);

        startService(intent);

        try {
            Logger.verbose("AirshipGcmTaskService - Waiting for job: " + job + " to complete.");
            latch.await();
        } catch (InterruptedException e) {
            Logger.error("Failed to wait for task: " + taskParams);
            return GcmNetworkManager.RESULT_FAILURE;
        }

        if (resultReceiver.resultCode == Job.JOB_RETRY) {
            Logger.verbose("AirshipGcmTaskService - Rescheduling job " + job);
            return GcmNetworkManager.RESULT_RESCHEDULE;
        } else {
            Logger.verbose("AirshipGcmTaskService - Job finished: " + job);
            return GcmNetworkManager.RESULT_SUCCESS;
        }
    }

    /**
     * ResultReceiver that captures the result as a field.
     */
    private static class JobResultReceiver extends ResultReceiver {
        int resultCode;

        JobResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            super.onReceiveResult(resultCode, resultData);
            this.resultCode = resultCode;
        }
    }
}

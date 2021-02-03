/* Copyright Airship and Contributors */

package com.urbanairship.job;

import android.content.Context;

import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

class WorkManagerScheduler implements Scheduler {
    private static final String AIRSHIP_TAG = "airship";

    @Override
    public void schedule(@NonNull Context context, @NonNull JobInfo jobInfo) throws SchedulerException {
        try {
            OneTimeWorkRequest workRequest = createWorkRequest(jobInfo);
            ExistingWorkPolicy workPolicy = convertConflict(jobInfo.getConflictStrategy());
            String uniqueName = jobInfo.getAirshipComponentName() + ":" + jobInfo.getAction();
            WorkManager.getInstance(context)
                       .enqueueUniqueWork(uniqueName, workPolicy, workRequest);
        } catch (Exception e) {
            throw new SchedulerException("Failed to schedule job", e);
        }
    }

    private static OneTimeWorkRequest createWorkRequest(@NonNull JobInfo jobInfo) {
        OneTimeWorkRequest.Builder workRequestBuilder = new OneTimeWorkRequest.Builder(AirshipWorker.class)
                .addTag(AIRSHIP_TAG)
                .setInputData(WorkUtils.convertToData(jobInfo))
                .setConstraints(createConstraints(jobInfo));

        if (jobInfo.getInitialDelay() > 0) {
            workRequestBuilder.setInitialDelay(jobInfo.getInitialDelay(), TimeUnit.MILLISECONDS);
        }

        return workRequestBuilder.build();
    }

    @NonNull
    private static ExistingWorkPolicy convertConflict(@JobInfo.ConflictStrategy int conflictStrategy) {
        ExistingWorkPolicy policy;
        switch (conflictStrategy) {
            case JobInfo.APPEND:
                policy = ExistingWorkPolicy.APPEND;
                break;
            case JobInfo.REPLACE:
                policy = ExistingWorkPolicy.REPLACE;
                break;
            case JobInfo.KEEP:
            default:
                policy = ExistingWorkPolicy.APPEND_OR_REPLACE;
                break;
        }

        return policy;
    }

    @NonNull
    private static Constraints createConstraints(@NonNull JobInfo jobInfo) {
        return new Constraints.Builder()
                .setRequiredNetworkType(jobInfo.isNetworkAccessRequired() ? NetworkType.CONNECTED : NetworkType.NOT_REQUIRED)
                .build();

    }

}

/* Copyright Airship and Contributors */

package com.urbanairship.job;

import android.content.Context;

import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

class WorkManagerScheduler implements Scheduler {
    static final String JOB_INFO = "job_info";
    private static final String AIRSHIP_TAG = "airship";

    @Override
    public void schedule(@NonNull Context context, @NonNull JobInfo jobInfo, long delayMs) throws SchedulerException {
        try {
            OneTimeWorkRequest workRequest = createWorkRequest(jobInfo, delayMs);
            ExistingWorkPolicy workPolicy = convertConflict(jobInfo.getConflictStrategy());
            String uniqueName = jobInfo.getAirshipComponentName() + ":" + jobInfo.getAction();
            WorkManager.getInstance(context)
                       .enqueueUniqueWork(uniqueName, workPolicy, workRequest);
        } catch (Exception e) {
            throw new SchedulerException("Failed to schedule job", e);
        }
    }

    private static OneTimeWorkRequest createWorkRequest(@NonNull JobInfo jobInfo, long delayMs) {
        Data data = WorkUtils.convertToData(jobInfo);
        OneTimeWorkRequest.Builder workRequestBuilder = new OneTimeWorkRequest.Builder(AirshipWorker.class)
                .addTag(AIRSHIP_TAG)
                .setInputData(data)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, jobInfo.getInitialBackOffMs(), TimeUnit.MILLISECONDS)
                .setConstraints(createConstraints(jobInfo));

        if (delayMs > 0) {
            workRequestBuilder.setInitialDelay(delayMs, TimeUnit.MILLISECONDS);
        }

        return workRequestBuilder.build();
    }

    @NonNull
    private static ExistingWorkPolicy convertConflict(@JobInfo.ConflictStrategy int conflictStrategy) {
        ExistingWorkPolicy policy;
        switch (conflictStrategy) {
            case JobInfo.APPEND:
                policy = ExistingWorkPolicy.APPEND_OR_REPLACE;
                break;
            case JobInfo.REPLACE:
                policy = ExistingWorkPolicy.REPLACE;
                break;
            case JobInfo.KEEP:
            default:
                policy = ExistingWorkPolicy.KEEP;
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

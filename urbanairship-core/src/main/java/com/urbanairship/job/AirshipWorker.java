/* Copyright Airship and Contributors */

package com.urbanairship.job;

import android.content.Context;

import com.google.common.util.concurrent.ListenableFuture;
import com.urbanairship.Logger;
import com.urbanairship.base.Extender;
import com.urbanairship.json.JsonException;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

/**
 * WorkManager worker for Airship Jobs.
 *
 * @hide
 */
public class AirshipWorker extends ListenableWorker {

    private static final int RESCHEDULE_RETRY_COUNT = 5;
    private static final long RESCHEDULE_RETRY_DELAY_MS = TimeUnit.HOURS.toMillis(1);

    public AirshipWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        return CallbackToFutureAdapter.getFuture(new CallbackToFutureAdapter.Resolver<Result>() {
            @Nullable
            @Override
            public Object attachCompleter(@NonNull final CallbackToFutureAdapter.Completer<Result> completer) throws Exception {
                JobInfo jobInfo;
                Data data = getInputData();
                try {
                    jobInfo = WorkUtils.convertToJobInfo(getInputData());
                } catch (JsonException e) {
                    Logger.error("Failed to parse jobInfo.");
                    return completer.set(Result.failure());
                }

                final UUID workId = getId();
                int runAttempt = getRunAttemptCount();
                JobRunnable job = JobRunnable.newBuilder(jobInfo)
                                             .setCallback((job1, result) -> {
                                                 switch (result) {
                                                     case JobInfo.JOB_FINISHED:
                                                         Logger.verbose("Job finished. Job info: %s, work Id: %s", jobInfo, workId);
                                                         completer.set(Result.success());
                                                         break;
                                                     case JobInfo.JOB_RETRY:
                                                         if (runAttempt >= RESCHEDULE_RETRY_COUNT) {
                                                             Logger.verbose("Job retry limit reached. Rescheduling for a later time. Job info: %s, work Id: %s", jobInfo, workId);
                                                             completer.set(Result.failure());
                                                             reschedule(getApplicationContext(), data);
                                                         } else {
                                                             Logger.verbose("Job failed, will retry. Job info: %s, work Id: %s", jobInfo, workId);
                                                             completer.set(Result.retry());
                                                         }
                                                         break;
                                                 }
                                             })
                                             .build();

                Logger.verbose("Running job: %s, work Id: %s run attempt: %s", jobInfo, workId, runAttempt);
                JobRunnable.EXECUTOR.execute(job);
                return jobInfo;
            }
        });
    }

    private static void reschedule(@NonNull Context context, @NonNull Data data) {

        try {
            JobInfo jobInfo = WorkUtils.convertToJobInfo(data, value -> {
                return value.setInitialDelay(RESCHEDULE_RETRY_DELAY_MS, TimeUnit.MILLISECONDS)
                            .setInitialBackOff(RESCHEDULE_RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
            });

            JobDispatcher.shared(context).dispatch(jobInfo);
        } catch (JsonException e) {
            Logger.error("Failed to reschedule job.", e);
        }
    }

}

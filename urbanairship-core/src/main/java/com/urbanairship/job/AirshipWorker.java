/* Copyright Airship and Contributors */

package com.urbanairship.job;

import android.content.Context;

import com.google.common.util.concurrent.ListenableFuture;
import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

/**
 * WorkManager worker for Airship Jobs.
 *
 * @hide
 */
public class AirshipWorker extends ListenableWorker {

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
                try {
                    jobInfo = WorkUtils.convertToJobInfo(getInputData());
                } catch (JsonException e) {
                    Logger.error("AirshipWorker: Failed to parse jobInfo.");
                    return completer.set(Result.failure());
                }

                JobRunnable job = JobRunnable.newBuilder(jobInfo)
                                             .setCallback(new JobRunnable.Callback() {
                                                 @Override
                                                 public void onFinish(@NonNull JobRunnable job, @JobInfo.JobResult int result) {
                                                     switch (result) {
                                                         case JobInfo.JOB_FINISHED:
                                                             completer.set(Result.success());
                                                             break;
                                                         case JobInfo.JOB_RETRY:
                                                             completer.set(Result.retry());
                                                             break;
                                                     }
                                                 }
                                             })
                                             .build();

                Logger.verbose("Running job: %s", jobInfo);

                JobRunnable.EXECUTOR.execute(job);
                return jobInfo;
            }
        });
    }

}

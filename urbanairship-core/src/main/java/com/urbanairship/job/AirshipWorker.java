/* Copyright Airship and Contributors */

package com.urbanairship.job;

import android.content.Context;

import com.google.common.util.concurrent.ListenableFuture;
import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;

import java.util.UUID;

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
        return CallbackToFutureAdapter.getFuture(completer -> {
            JobInfo jobInfo = parseJobInfo();

            if (jobInfo == null) {
                return completer.set(Result.failure());
            }

            final UUID workId = getId();
            int runAttempt = getRunAttemptCount();

            Logger.verbose("Running job: %s, work Id: %s run attempt: %s", jobInfo, workId, runAttempt);

            JobDispatcher.shared(getApplicationContext()).onStartJob(jobInfo, runAttempt, jobResult -> {
                switch (jobResult) {
                    case RETRY:
                        completer.set(Result.retry());
                    case FAILURE:
                        completer.set(Result.failure());
                    case SUCCESS:
                        completer.set(Result.success());
                }
            });
            return jobInfo;
        });
    }

    @Nullable
    private JobInfo parseJobInfo() {
        try {
            return WorkUtils.convertToJobInfo(getInputData());
        } catch (JsonException e) {
            Logger.error(e, "Failed to parse jobInfo.");
            return null;
        }
    }
}

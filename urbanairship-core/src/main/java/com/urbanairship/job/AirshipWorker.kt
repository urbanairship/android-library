/* Copyright Airship and Contributors */
package com.urbanairship.job

import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.google.common.util.concurrent.ListenableFuture

/**
 * WorkManager worker for Airship Jobs.
 *
 * @hide
 */
internal class AirshipWorker (
    context: Context,
    workerParams: WorkerParameters
) : ListenableWorker(context, workerParams) {

    override fun startWork(): ListenableFuture<Result> {
        return CallbackToFutureAdapter.getFuture { completer: CallbackToFutureAdapter.Completer<Result> ->
            val jobInfo = parseJobInfo() ?: return@getFuture completer.set(Result.failure())
            val workId = id
            val runAttempt = runAttemptCount

            UALog.v("Running job: $jobInfo, work Id: $workId run attempt: $runAttempt")

            JobDispatcher.shared(applicationContext)
                .onStartJob(jobInfo, runAttempt.toLong()) { jobResult: JobResult ->
                    when (jobResult) {
                        JobResult.RETRY -> completer.set(Result.retry())
                        JobResult.FAILURE -> completer.set(Result.failure())
                        JobResult.SUCCESS -> completer.set(Result.success())
                    }
                }

            jobInfo
        }
    }

    private fun parseJobInfo(): JobInfo? {
        return try {
            WorkUtils.convertToJobInfo(inputData)
        } catch (e: JsonException) {
            UALog.e(e, "Failed to parse jobInfo.")
            null
        }
    }
}

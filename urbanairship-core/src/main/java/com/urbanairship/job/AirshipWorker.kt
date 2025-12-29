/* Copyright Airship and Contributors */
package com.urbanairship.job

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import androidx.work.ListenableWorker.Result as WorkerResult

/**
 * WorkManager worker for Airship Jobs.
 *
 * @hide
 */
internal class AirshipWorker (
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): WorkerResult {
        val jobInfo = parseJobInfo() ?: return WorkerResult.failure()
        val workId = id
        val runAttempt = runAttemptCount

        UALog.v("Running job: $jobInfo, work Id: $workId run attempt: $runAttempt")
        val jobResult = JobDispatcher.shared(applicationContext).runJob(jobInfo, runAttempt.toLong())
        return when (jobResult) {
            JobResult.RETRY -> WorkerResult.retry()
            JobResult.FAILURE -> WorkerResult.failure()
            JobResult.SUCCESS -> WorkerResult.success()
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

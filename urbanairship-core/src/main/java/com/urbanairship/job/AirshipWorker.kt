/* Copyright Airship and Contributors */
package com.urbanairship.job

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result as WorkerResult
import kotlin.Result as KotlinResult
import androidx.work.WorkerParameters
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import kotlin.coroutines.suspendCoroutine

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
        return suspendCoroutine { continuation ->
            JobDispatcher.shared(applicationContext)
                .onStartJob(jobInfo, runAttempt.toLong()) { jobResult: JobResult ->
                    when (jobResult) {
                        JobResult.RETRY -> KotlinResult.success(WorkerResult.retry())
                        JobResult.FAILURE -> KotlinResult.success(WorkerResult.failure())
                        JobResult.SUCCESS -> KotlinResult.success(WorkerResult.success())
                    }.let { result ->
                        continuation.resumeWith(result)
                    }
                }
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

/* Copyright Airship and Contributors */
package com.urbanairship.job

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.urbanairship.job.JobInfo.ConflictStrategy
import com.urbanairship.job.WorkUtils.convertToData
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

internal class WorkManagerScheduler : Scheduler {

    @Throws(SchedulerException::class)
    override fun schedule(context: Context, jobInfo: JobInfo, delay: Duration) {
        try {
            val workRequest = createWorkRequest(jobInfo, delay)
            val workPolicy = convertConflict(jobInfo.conflictStrategy)
            val uniqueName = jobInfo.airshipComponentName + ":" + jobInfo.action
            WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, workPolicy, workRequest)
        } catch (e: Exception) {
            throw SchedulerException("Failed to schedule job", e)
        }
    }

    companion object {
        private const val AIRSHIP_TAG = "airship"

        private fun createWorkRequest(jobInfo: JobInfo, delay: Duration): OneTimeWorkRequest {
            val data = convertToData(jobInfo)
            val workRequestBuilder = OneTimeWorkRequest.Builder(AirshipWorker::class.java)
                .addTag(AIRSHIP_TAG)
                .setInputData(data)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, jobInfo.initialBackOff.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .setConstraints(createConstraints(jobInfo))

            if (delay.isPositive()) {
                workRequestBuilder.setInitialDelay(delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            }

            return workRequestBuilder.build()
        }

        private fun convertConflict(conflictStrategy: ConflictStrategy): ExistingWorkPolicy {
            return when (conflictStrategy) {
                ConflictStrategy.APPEND -> ExistingWorkPolicy.APPEND_OR_REPLACE
                ConflictStrategy.REPLACE -> ExistingWorkPolicy.REPLACE
                ConflictStrategy.KEEP -> ExistingWorkPolicy.KEEP
            }
        }

        private fun createConstraints(jobInfo: JobInfo): Constraints {
            val networkType = if (jobInfo.isNetworkAccessRequired) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED

            return Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()
        }
    }
}

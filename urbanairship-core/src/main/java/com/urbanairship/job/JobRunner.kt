/* Copyright Airship and Contributors */

package com.urbanairship.job

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.Airship
import com.urbanairship.UALog
import com.urbanairship.util.SerialQueue
import java.lang.ref.WeakReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.seconds

@VisibleForTesting
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface JobRunner {

    public suspend fun run(jobInfo: JobInfo): JobResult

    public fun addJobHandler(
        scope: String,
        jobActions: List<String>,
        jobHandler: suspend (JobInfo) -> JobResult
    )

    public fun <T> addWeakJobHandler(
        component: T,
        jobActions: List<String>,
        handler: suspend T.(JobInfo) -> JobResult
    ) {
        val scope = component?.javaClass?.name ?: ""
        val weakRef = WeakReference(component)
        addJobHandler(scope, jobActions) { jobInfo ->
            weakRef.get()?.let { handler(it, jobInfo) } ?: JobResult.FAILURE
        }
    }

    public class DefaultRunner() : JobRunner {

        private val entryLock = ReentrantLock()
        private val entries: MutableMap<String, JobHandlerEntry> = mutableMapOf()


        override suspend fun run(jobInfo: JobInfo): JobResult {
            if (!Airship.waitForReady(AIRSHIP_WAIT_TIME)) {
                UALog.e { "Airship not ready. Rescheduling job: $jobInfo" }
                return JobResult.RETRY
            }

            val key = makeEntryKey(jobInfo.scope, jobInfo.action)
            val entry = entryLock.withLock {
                entries[key]
            }

            if (entry == null) {
                UALog.e { "No entries found for action ${jobInfo.action}. Rescheduling job: $jobInfo" }
                return JobResult.FAILURE
            }

            return try {
                // Use SerialQueue to ensure jobs run in FIFO sequential order for this handler
                // All jobs for the same handler entry will execute one at a time in the order they arrive
                entry.queue.run {
                    entry.jobHandler.invoke(jobInfo)
                }
            } catch (e: Exception) {
                // Log the exception but don't let it propagate to other entries
                // The exception is caught here so it doesn't break the FIFO queue for other jobs
                UALog.e(e) { "Job handler threw exception for job: $jobInfo" }
                JobResult.FAILURE
            }
        }

        override fun addJobHandler(
            scope: String,
            jobActions: List<String>,
            jobHandler: suspend (JobInfo) -> JobResult
        ) {
            val entry = JobHandlerEntry(jobHandler = jobHandler)
            entryLock.withLock {
                jobActions.forEach { action ->
                    val key = makeEntryKey(scope, action)
                    if (entries[key] != null) {
                        UALog.e { "Duplicate job action: $key" }
                        return
                    }

                    entries[key] = entry
                }
            }
        }

        private fun makeEntryKey(scope: String, action: String): String {
            return "$scope.$action"
        }

        private companion object {
            private val AIRSHIP_WAIT_TIME = 5.seconds
        }
    }

    private data class JobHandlerEntry(
        val queue: SerialQueue = SerialQueue(),
        val jobHandler: suspend (JobInfo) -> JobResult
    )
}

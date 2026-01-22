/* Copyright Airship and Contributors */
package com.urbanairship.job

import android.content.Context
import androidx.annotation.IntRange
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import com.urbanairship.job.JobRunner.DefaultRunner
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Dispatches jobs.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class JobDispatcher public constructor(
    context: Context,
    private val scheduler: Scheduler = WorkManagerScheduler(),
    private val jobRunner: JobRunner = DefaultRunner(),
    private val rateLimiter: RateLimiter = RateLimiter(),
    private val dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher()
) {

    private val scheduleScope = CoroutineScope(dispatcher + SupervisorJob())

    private val context: Context = context.applicationContext
    private val pendingJobInfos = MutableStateFlow<List<Pending>>(emptyList())

    private val retryJobLock = ReentrantLock()
    private var retryJob: Job? = null

    /**
     * Sets a rate limit.
     *
     * @param limitId Limit Id.
     * @param rate The number of events for the duration.
     * @param duration The duration.
     */
    public fun setRateLimit(
        limitId: String,
        @IntRange(from = 1) rate: Int,
        duration: Duration
    ) {
        rateLimiter.setLimit(limitId, rate, duration)
    }

    /**
     * Dispatches a jobInfo to be performed immediately.
     *
     * @param jobInfo The jobInfo.
     */
    public fun dispatch(jobInfo: JobInfo) {
        dispatch(jobInfo, getDelay(jobInfo))
    }

    private fun dispatch(jobInfo: JobInfo, delay: Duration) {
        try {
            dispatchPending()
            scheduler.schedule(context, jobInfo, delay)
        } catch (e: Exception) {
            UALog.e(e, "Scheduler failed to schedule jobInfo")
            pendingJobInfos.update { it + Pending(jobInfo, delay) }
        }

        if (pendingJobInfos.value.isNotEmpty()) {
            schedulePending()
        }
    }

    private fun schedulePending() {
        retryJobLock.withLock {
            retryJob?.cancel()
            retryJob = scheduleScope.launch {
                delay(RETRY_DELAY)
                try {
                    dispatchPending()
                } catch (e: Exception) {
                    UALog.e(e, "Failed to dispatch pending jobs, will retry")
                    schedulePending()
                }
            }
        }
    }

    private fun dispatchAsync(jobInfo: JobInfo, delay: Duration) {
        scheduleScope.launch {
            dispatch(jobInfo, delay)
        }
    }

    private fun dispatchPending() {
        var exception: Exception? = null

        pendingJobInfos.update { current ->
            val processed = mutableSetOf<Pending>()
            for (item in current) {
                try {
                    scheduler.schedule(context, item.jobInfo, item.delay)
                    processed.add(item)
                } catch (e: Exception) {
                    exception = e
                    return@update current - processed
                }
            }

            emptyList()
        }

        exception?.let { throw it }
    }

    public fun addJobHandler(
        scope: String,
        actions: List<String>,
        handler: suspend (JobInfo) -> JobResult
    ) {
        jobRunner.addJobHandler(scope, actions, handler)
    }

    public fun <T> addWeakJobHandler(
        component: T,
        actions: List<String>,
        handler: suspend T.(JobInfo) -> JobResult
    ) {
        jobRunner.addWeakJobHandler(component, actions, handler)
    }

    public suspend fun runJob(jobInfo: JobInfo, runAttempt: Long): JobResult {
        UALog.v("Running job: $jobInfo, run attempt: $runAttempt")

        val rateLimitDelay = getRateLimitDelay(jobInfo)
        if (rateLimitDelay.isPositive()) {
            // Schedule asynchronously after returning to avoid potential WorkManager conflicts
            // with the current work execution that's still in progress
            dispatchAsync(jobInfo, rateLimitDelay)
            return JobResult.FAILURE
        }

        for (rateLimitID in jobInfo.rateLimitIds) {
            rateLimiter.track(rateLimitID)
        }


        val result = jobRunner.run(jobInfo)
        UALog.v("Job finished. Job info: $jobInfo, result: $result")
        val shouldRetry = result == JobResult.RETRY
        val shouldReschedule = runAttempt >= RESCHEDULE_RETRY_COUNT

        // Workaround for APPEND jobs, which we don't want to reschedule like other jobs.
        val isAppend = jobInfo.conflictStrategy == JobInfo.ConflictStrategy.APPEND
        if (shouldRetry && shouldReschedule && !isAppend) {
            UALog.v("Job retry limit reached. Rescheduling for a later time. Job info: $jobInfo")
            // Schedule asynchronously after returning to avoid potential WorkManager conflicts
            // with the current work execution that's still in progress
            dispatchAsync(jobInfo, RESCHEDULE_RETRY_DELAY)
            return JobResult.FAILURE
        }

        return result
    }

    private fun getDelay(jobInfo: JobInfo): Duration {
        return maxOf(jobInfo.minDelay, getRateLimitDelay(jobInfo))
    }

    private fun getRateLimitDelay(jobInfo: JobInfo): Duration {
        return jobInfo.rateLimitIds
            .mapNotNull(rateLimiter::status)
            .filter { it.limitStatus == RateLimiter.LimitStatus.OVER }
            .maxOfOrNull { it.nextAvailable }
            ?: 0.seconds
    }

    private data class Pending(val jobInfo: JobInfo, val delay: Duration)

    public companion object {

        public val RESCHEDULE_RETRY_DELAY: Duration = 1.hours

        private const val RESCHEDULE_RETRY_COUNT = 5
        private val RETRY_DELAY = 1000.milliseconds

        @Volatile private var instance: JobDispatcher? = null

        /**
         * Gets the shared instance.
         *
         * @param context The application context.
         * @return The [JobDispatcher].
         */
        public fun shared(context: Context): JobDispatcher {
            return instance ?: synchronized(JobDispatcher::class.java) {
                instance ?: JobDispatcher(context).also { instance = it }
            }
        }

        @VisibleForTesting
        public fun setInstance(dispatcher: JobDispatcher) {
            synchronized(JobDispatcher::class.java) {
                instance = dispatcher
            }
        }
    }
}

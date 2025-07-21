/* Copyright Airship and Contributors */
package com.urbanairship.job

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.IntRange
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.core.util.Consumer
import com.urbanairship.UALog
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobRunner.DefaultRunner
import com.urbanairship.job.SchedulerException
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

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
    private val rateLimiter: RateLimiter = RateLimiter()
) {

    private val context: Context = context.applicationContext
    private val pendingJobInfos = MutableStateFlow<List<Pending>>(emptyList())

    private val retryPendingRunnable = Runnable {
        try {
            dispatchPending()
        } catch (_: SchedulerException) {
            schedulePending()
        }
    }

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
        } catch (e: SchedulerException) {
            UALog.e(e, "Scheduler failed to schedule jobInfo")
            pendingJobInfos.update { it + Pending(jobInfo, delay) }
        }
        schedulePending()
    }

    private fun schedulePending() {
        val handler = Handler(Looper.getMainLooper())
        handler.removeCallbacks(retryPendingRunnable)
        handler.postDelayed(retryPendingRunnable, RETRY_DELAY.inWholeMilliseconds)
    }

    @Throws(SchedulerException::class)
    private fun dispatchPending() {
        var exception: SchedulerException? = null

        pendingJobInfos.update { current ->
            val processed = mutableSetOf<Pending>()
            for (item in current) {
                try {
                    scheduler.schedule(context, item.jobInfo, item.delay)
                    processed.add(item)
                } catch (e: SchedulerException) {
                    exception = e
                    return@update current - processed
                }
            }

            emptyList()
        }

        exception?.let { throw it }
    }

    public fun onStartJob(jobInfo: JobInfo, runAttempt: Long, callback: Consumer<JobResult>) {
        UALog.v("Running job: $jobInfo, run attempt: $runAttempt")

        val rateLimitDelay = getRateLimitDelay(jobInfo)
        if (rateLimitDelay.isPositive()) {
            callback.accept(JobResult.FAILURE)
            dispatch(jobInfo, rateLimitDelay)
            return
        }

        for (rateLimitID in jobInfo.rateLimitIds) {
            rateLimiter.track(rateLimitID)
        }

        jobRunner.run(jobInfo) { result: JobResult ->
            UALog.v("Job finished. Job info: $jobInfo, result: $result")
            val shouldRetry = result == JobResult.RETRY
            val shouldReschedule = runAttempt >= RESCHEDULE_RETRY_COUNT
            // Workaround for APPEND jobs, which we don't want to reschedule like other jobs.
            val isAppend = jobInfo.conflictStrategy == JobInfo.ConflictStrategy.APPEND
            if (shouldRetry && shouldReschedule && !isAppend) {
                UALog.v("Job retry limit reached. Rescheduling for a later time. Job info: $jobInfo")
                dispatch(jobInfo, RESCHEDULE_RETRY_DELAY)
                callback.accept(JobResult.FAILURE)
            } else {
                callback.accept(result)
            }
        }
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
        @JvmStatic //TODO: il remove when push module is migrated
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

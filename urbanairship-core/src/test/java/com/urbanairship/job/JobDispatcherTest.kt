/* Copyright Airship and Contributors */
package com.urbanairship.job

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.push.PushManager
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
public class JobDispatcherTest {
    private val testDispatcher = StandardTestDispatcher()
    private val mockRateLimiter: RateLimiter = mockk(relaxed = true)
    private val jobRunner = TestJobRunner()
    private val mockScheduler: Scheduler = mockk(relaxed = true)
    private val context: Context = ApplicationProvider.getApplicationContext<Context>()
    private val dispatcher = JobDispatcher(context, mockScheduler, jobRunner, mockRateLimiter, testDispatcher)

    @Test
    public fun testDispatch() {
        val jobInfo = JobInfo.newBuilder()
            .setAction("test_action")
            .setScope(PushManager::class.java.name)
            .setMinDelay(10.milliseconds)
            .build()

        dispatcher.dispatch(jobInfo)

        verify { mockScheduler.schedule(context, jobInfo, 10.milliseconds) }
    }

    @Test
    public fun testStartJob(): TestResult = runTest(testDispatcher) {
        val jobInfo = JobInfo.newBuilder()
            .setAction("test_action")
            .setScope(PushManager::class.java.name)
            .build()

        jobRunner.result = JobResult.FAILURE

        val result = dispatcher.runJob(jobInfo, 1)
        advanceUntilIdle()

        assert(result == JobResult.FAILURE)
        verify { mockScheduler wasNot Called }
        Assert.assertEquals(jobInfo, jobRunner.lastJob)
    }

    @Test
    public fun testMaxRetries(): TestResult = runTest(testDispatcher) {
        val jobInfo = JobInfo.newBuilder()
            .setAction("test_action")
            .setScope(PushManager::class.java.name)
            .build()

        jobRunner.result = JobResult.RETRY

        var result = dispatcher.runJob(jobInfo, 4)
        advanceUntilIdle()
        assert(result == JobResult.RETRY)
        verify { mockScheduler wasNot Called }

        result = dispatcher.runJob(jobInfo, 5)
        advanceUntilIdle()
        assert(result == JobResult.FAILURE)
        verify { mockScheduler.schedule(context, jobInfo, JobDispatcher.RESCHEDULE_RETRY_DELAY) }
    }

    @Test
    public fun testSetRateLimit() {
        dispatcher.setRateLimit("foo", 19, 100.days)
        verify { mockRateLimiter.setLimit("foo", 19, 100.days) }
    }

    @Test
    public fun testDispatchRateLimit() {
        val jobInfo = JobInfo.newBuilder()
            .setAction("test_action")
            .setScope(PushManager::class.java.name)
            .setMinDelay(10.milliseconds)
            .addRateLimit("rateOne")
            .addRateLimit("rateTwo")
            .build()

        every { mockRateLimiter.status("rateOne") } answers {
            RateLimiter.Status(RateLimiter.LimitStatus.OVER, 100.milliseconds)
        }

        every { mockRateLimiter.status("rateTwo") } answers {
            RateLimiter.Status(RateLimiter.LimitStatus.OVER, 300.milliseconds)
        }

        dispatcher.dispatch(jobInfo)

        verify { mockScheduler.schedule(context, jobInfo, 300.milliseconds) }
    }

    @Test
    public fun testStartJobTracksRateLimits(): TestResult = runTest(testDispatcher) {
        val jobInfo = JobInfo.newBuilder()
            .setAction("test_action")
            .setScope(PushManager::class.java.name)
            .setMinDelay(10.milliseconds)
            .addRateLimit("rateOne")
            .addRateLimit("rateTwo")
            .build()

        dispatcher.runJob(jobInfo, 4)
        advanceUntilIdle()

        verify { mockRateLimiter.track("rateOne") }
        verify { mockRateLimiter.track("rateTwo") }
    }

    @Test
    public fun testStartJobOverRateLimit(): TestResult = runTest(testDispatcher) {
        val jobInfo = JobInfo.newBuilder()
            .setAction("test_action")
            .setScope(PushManager::class.java.name)
            .setMinDelay(10.milliseconds)
            .addRateLimit("rateOne")
            .addRateLimit("rateTwo")
            .build()

        every { mockRateLimiter.status("rateOne") } answers {
            RateLimiter.Status(RateLimiter.LimitStatus.OVER, 100.milliseconds)
        }
        every { mockRateLimiter.status("rateTwo") } answers {
            RateLimiter.Status(RateLimiter.LimitStatus.UNDER, 0.milliseconds)
        }

        val result = dispatcher.runJob(jobInfo, 4)
        advanceUntilIdle()

        assert(result == JobResult.FAILURE)
        verify(exactly = 0) { mockRateLimiter.track(any()) }
        Assert.assertNull(jobRunner.lastJob)

        verify { mockScheduler.schedule(context, jobInfo, 100.milliseconds) }
    }

    private class TestJobRunner : JobRunner {

        var result: JobResult = JobResult.SUCCESS
        var lastJob: JobInfo? = null

        override suspend fun run(jobInfo: JobInfo): JobResult {
            lastJob = jobInfo
            return result
        }

        override fun addJobHandler(
            scope: String, jobActions: List<String>, jobHandler: suspend (JobInfo) -> JobResult
        ) {
        }

    }
}

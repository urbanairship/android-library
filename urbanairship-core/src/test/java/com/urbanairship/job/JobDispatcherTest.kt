/* Copyright Airship and Contributors */
package com.urbanairship.job

import androidx.core.util.Consumer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestApplication
import com.urbanairship.push.PushManager
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class JobDispatcherTest {

    private val mockRateLimiter: RateLimiter = mockk(relaxed = true)
    private val jobRunner = TestJobRunner()
    private val mockScheduler: Scheduler = mockk(relaxed = true)
    private val context: TestApplication = TestApplication.getApplication()
    private val dispatcher = JobDispatcher(context, mockScheduler, jobRunner, mockRateLimiter)
    private val mockConsumer: Consumer<JobResult> = mockk(relaxed = true)

    @Test
    public fun testDispatch() {
        val jobInfo = JobInfo.newBuilder()
            .setAction("test_action")
            .setAirshipComponent(PushManager::class.java)
            .setMinDelay(10.milliseconds)
            .build()

        dispatcher.dispatch(jobInfo)

        verify { mockScheduler.schedule(context, jobInfo, 10.milliseconds) }
    }

    @Test
    public fun testStartJob() {
        val jobInfo = JobInfo.newBuilder()
            .setAction("test_action")
            .setAirshipComponent(PushManager::class.java)
            .build()

        jobRunner.result = JobResult.FAILURE

        dispatcher.onStartJob(jobInfo, 1, mockConsumer)
        verify { mockConsumer.accept(JobResult.FAILURE) }
        verify { mockScheduler wasNot Called }
        Assert.assertEquals(jobInfo, jobRunner.lastJob)
    }

    @Test
    public fun testMaxRetries() {
        val jobInfo = JobInfo.newBuilder()
            .setAction("test_action")
            .setAirshipComponent(PushManager::class.java)
            .build()

        jobRunner.result = JobResult.RETRY

        dispatcher.onStartJob(jobInfo, 4, mockConsumer)
        verify { mockConsumer.accept(JobResult.RETRY) }
        verify { mockScheduler wasNot Called }

        dispatcher.onStartJob(jobInfo, 5, mockConsumer)
        verify { mockConsumer.accept(JobResult.FAILURE) }
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
            .setAirshipComponent(PushManager::class.java)
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
    public fun testStartJobTracksRateLimits() {
        val jobInfo = JobInfo.newBuilder()
            .setAction("test_action")
            .setAirshipComponent(PushManager::class.java)
            .setMinDelay(10.milliseconds)
            .addRateLimit("rateOne")
            .addRateLimit("rateTwo")
            .build()

        dispatcher.onStartJob(jobInfo, 4, mockConsumer)

        verify { mockRateLimiter.track("rateOne") }
        verify { mockRateLimiter.track("rateTwo") }
    }

    @Test
    public fun testStartJobOverRateLimit() {
        val jobInfo = JobInfo.newBuilder()
            .setAction("test_action")
            .setAirshipComponent(PushManager::class.java)
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

        dispatcher.onStartJob(jobInfo, 4, mockConsumer)
        verify { mockConsumer.accept(JobResult.FAILURE) }
        verify(exactly = 0) { mockRateLimiter.track(any()) }
        Assert.assertNull(jobRunner.lastJob)

        verify { mockScheduler.schedule(context, jobInfo, 100.milliseconds) }
    }

    private class TestJobRunner : JobRunner {

        var result: JobResult = JobResult.SUCCESS
        var lastJob: JobInfo? = null
        override fun run(jobInfo: JobInfo, resultConsumer: Consumer<JobResult>) {
            lastJob = jobInfo
            resultConsumer.accept(result)
        }
    }
}

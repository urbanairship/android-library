package com.urbanairship.job

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.Airship
import com.urbanairship.job.JobRunner.DefaultRunner
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class JobRunnerTest {

    private val runner = DefaultRunner()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkObject(Airship)
        coEvery { Airship.waitForReady(any()) } returns true
    }

    @After
    fun tearDown() {
        mockkObject(Airship)
    }

    @Test
    fun testRunJob() = runTest {
        val jobInfo = JobInfo.newBuilder()
            .setScope("scope")
            .setAction("action")
            .build()

        runner.addJobHandler("scope", listOf("action")) {
            JobResult.SUCCESS
        }

        val result = runner.run(jobInfo)
        assertEquals(JobResult.SUCCESS, result)
    }

    @Test
    fun testRunJobNoHandler() = runTest {
        val jobInfo = JobInfo.newBuilder()
            .setScope("scope")
            .setAction("action")
            .build()

        val result = runner.run(jobInfo)
        assertEquals(JobResult.FAILURE, result)
    }

    @Test
    fun testRunJobAirshipNotReady() = runTest {
        coEvery { Airship.waitForReady(any()) } returns false

        val jobInfo = JobInfo.newBuilder()
            .setScope("scope")
            .setAction("action")
            .build()

        val result = runner.run(jobInfo)
        assertEquals(JobResult.RETRY, result)
    }

    @Test
    fun testRunJobException() = runTest {
        val jobInfo = JobInfo.newBuilder()
            .setScope("scope")
            .setAction("action")
            .build()

        runner.addJobHandler("scope", listOf("action")) {
            throw Exception("Boom")
        }

        val result = runner.run(jobInfo)
        assertEquals(JobResult.FAILURE, result)
    }

    @Test
    fun testAddWeakJobHandler() = runTest {
        var called = false
        val component = object {
            fun onJob(job: JobInfo): JobResult {
                called = true
                return JobResult.SUCCESS
            }
        }

        runner.addWeakJobHandler(component, listOf("action")) {
             component.onJob(it)
        }

        val weakJobInfo = JobInfo.newBuilder()
            .setScope(component.javaClass.name)
            .setAction("action")
            .build()

        val result = runner.run(weakJobInfo)
        assertEquals(JobResult.SUCCESS, result)
        assertEquals(true, called)
    }
}

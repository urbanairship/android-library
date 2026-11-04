/* Copyright Airship and Contributors */
package com.urbanairship.push

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.Airship
import com.urbanairship.TestPushProvider
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class PushProviderBridgeTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val message = PushMessage(mapOf(PushMessage.EXTRA_ALERT to "alert"))

    private val processedOn = AtomicReference<Thread>()
    private val started = CountDownLatch(1)
    private val release = CountDownLatch(1)
    private val processed = CountDownLatch(1)

    @Before
    public fun setup() {
        mockkObject(Airship)
        every { Airship.waitForReadyBlocking(any()) } answers {
            started.countDown()
            release.await(10, TimeUnit.SECONDS)
            processedOn.set(Thread.currentThread())
            processed.countDown()
            false
        }
    }

    @After
    public fun tearDown() {
        release.countDown()
        unmockkObject(Airship)
    }

    private fun request() = PushProviderBridge.processPush(TestPushProvider::class.java, message)

    private fun executeSyncOnBackgroundThread(request: PushProviderBridge.ProcessPushRequest): CountDownLatch {
        val finished = CountDownLatch(1)
        Thread {
            request.executeSync(context)
            finished.countDown()
        }.start()
        return finished
    }

    @Test
    public fun testExecuteSyncOffMainThreadBlocks() {
        val finished = executeSyncOnBackgroundThread(request())

        assertTrue(started.await(10, TimeUnit.SECONDS))
        assertFalse(finished.await(100, TimeUnit.MILLISECONDS))

        release.countDown()
        assertTrue(finished.await(10, TimeUnit.SECONDS))
        assertEquals(0L, processed.count)
    }

    @Test
    public fun testExecuteSyncOnMainThreadDoesNotBlock() {
        assertSame(Looper.getMainLooper().thread, Thread.currentThread())

        request().executeSync(context)
        assertEquals(1L, processed.count)

        release.countDown()
        assertTrue(processed.await(10, TimeUnit.SECONDS))
        assertNotSame(Thread.currentThread(), processedOn.get())
    }

    @Test
    public fun testExecuteRunsOffCallerThread() {
        release.countDown()

        runBlocking { request().execute(context) }

        assertNotNull(processedOn.get())
        assertNotSame(Thread.currentThread(), processedOn.get())
    }

    @Test
    public fun testExecuteSwallowsProcessingFailures() {
        every { Airship.waitForReadyBlocking(any()) } throws IllegalStateException("boom")

        runBlocking { request().execute(context) }
    }

    @Test
    public fun testExecutePropagatesCancellation() {
        var resumed = false

        runBlocking {
            val job = launch(Dispatchers.Default) {
                request().execute(context)
                resumed = true
            }
            assertTrue(started.await(10, TimeUnit.SECONDS))
            job.cancel()
            release.countDown()
            job.join()
        }

        assertFalse(resumed)
    }

    @Test
    public fun testExecuteSyncReturnsAfterMaxCallbackWaitTime() {
        val finished = executeSyncOnBackgroundThread(request().setMaxCallbackWaitTimeMs(200))

        assertTrue(finished.await(10, TimeUnit.SECONDS))
        assertEquals(1L, processed.count)

        release.countDown()
        assertTrue(processed.await(10, TimeUnit.SECONDS))
    }

    @Test
    public fun testMaxCallbackWaitTimeSwallowsProcessingFailures() {
        every { Airship.waitForReadyBlocking(any()) } throws IllegalStateException("boom")
        val uncaught = AtomicReference<Throwable>()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught.set(e) }

        try {
            runBlocking { request().setMaxCallbackWaitTimeMs(1000).execute(context) }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(defaultHandler)
        }

        assertNull(uncaught.get())
    }
}

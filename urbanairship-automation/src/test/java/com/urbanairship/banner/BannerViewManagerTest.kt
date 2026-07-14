/* Copyright Airship and Contributors */

package com.urbanairship.banner

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.display.DisplayArgs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class BannerViewManagerTest {

    private val listeners = mutableMapOf<String, ThomasListenerInterface>()

    @After
    public fun teardown() {
        // The manager is a process-wide singleton, so clear all of its state between tests to
        // keep them order-independent.
        BannerViewManager.dismissAll()
        listeners.clear()
    }

    // https://github.com/cashapp/turbine/issues/92
    // Turbine has an issue with SharedFlow or WhileSubscribed, using a
    // job to cancel the test seems to fix it.

    @Test
    public fun testAddPendingEmitsRequest(): TestResult = runTest {
        val job = Job()
        BannerViewManager.displayRequests(scope = this + job).test {
            assertNull(awaitItem().next)

            addPending("banner", 0)

            val result = awaitItem()
            assertEquals("banner", result.next?.viewInstanceId)
            assertEquals(listOf("banner"), result.list.map { it.viewInstanceId })

            cancelAndIgnoreRemainingEvents()
        }
        job.cancel()
    }

    @Test
    public fun testPriorityDisplayOrder(): TestResult = runTest {
        addPending("low priority", 100)
        addPending("high priority", -100)
        addPending("medium priority", 0)

        val job = Job()
        BannerViewManager.displayRequests(scope = this + job).test {
            assertEquals("high priority", awaitItem().next?.viewInstanceId)
            BannerViewManager.dismiss("high priority")

            assertEquals("medium priority", awaitItem().next?.viewInstanceId)
            BannerViewManager.dismiss("medium priority")

            assertEquals("low priority", awaitItem().next?.viewInstanceId)
            cancelAndIgnoreRemainingEvents()
        }
        job.cancel()
    }

    @Test
    public fun testDisplayedBannerIsNotPreempted(): TestResult = runTest {
        val job = Job()
        BannerViewManager.displayRequests(scope = this + job).test {
            assertNull(awaitItem().next)

            addPending("displayed", 0)
            assertEquals("displayed", awaitItem().next?.viewInstanceId)

            // Adding a higher priority banner mid-display must not preempt the displayed one.
            addPending("higher priority", -100)

            val result = awaitItem()
            assertEquals("displayed", result.next?.viewInstanceId)
            assertEquals(2, result.list.size)

            cancelAndIgnoreRemainingEvents()
        }
        job.cancel()
    }

    @Test
    public fun testDismissAdvancesToNextPending(): TestResult = runTest {
        addPending("first", 0)
        addPending("second", 10)

        val job = Job()
        BannerViewManager.displayRequests(scope = this + job).test {
            assertEquals("first", awaitItem().next?.viewInstanceId)

            // Dismissing an unknown view instance ID is a no-op.
            BannerViewManager.dismiss("unknown")
            expectNoEvents()

            BannerViewManager.dismiss("first")
            assertEquals("second", awaitItem().next?.viewInstanceId)

            cancelAndIgnoreRemainingEvents()
        }
        job.cancel()
    }

    @Test
    public fun testDismissAll(): TestResult = runTest {
        addPending("first", 0)
        addPending("second", 10)
        addPending("third", 20)

        val job = Job()
        BannerViewManager.displayRequests(scope = this + job).test {
            assertEquals("first", awaitItem().next?.viewInstanceId)

            BannerViewManager.dismissAll()

            val result = awaitItem()
            assertNull(result.next)
            assertTrue(result.list.isEmpty())

            // Each dropped request is resolved with a cancel via its display listener.
            listOf("first", "second", "third").forEach { id ->
                verify(exactly = 1) { requireNotNull(listeners[id]).onDismiss(cancel = true) }
            }

            // lastViewed is cleared, so a newly added banner is selected fresh.
            addPending("fourth", 0)
            assertEquals("fourth", awaitItem().next?.viewInstanceId)

            cancelAndIgnoreRemainingEvents()
        }
        job.cancel()
    }

    @Test
    public fun testDismissNeverDisplayedResolvesListener(): TestResult = runTest {
        // No displayRequests subscription, so the request is never displayed.
        addPending("never displayed", 0)

        BannerViewManager.dismiss("never displayed")

        verify(exactly = 1) {
            requireNotNull(listeners["never displayed"]).onDismiss(cancel = true)
        }
    }

    @Test
    public fun testDismissDisplayedDoesNotResolveListener(): TestResult = runTest {
        addPending("displayed", 0)

        val job = Job()
        BannerViewManager.displayRequests(scope = this + job).test {
            assertEquals("displayed", awaitItem().next?.viewInstanceId)

            BannerViewManager.dismiss("displayed")
            assertNull(awaitItem().next)

            cancelAndIgnoreRemainingEvents()
        }
        job.cancel()

        // Displayed banners resolve their own display requests via the display listener when
        // they're dismissed, so the manager must not double-resolve them.
        verify(exactly = 0) { requireNotNull(listeners["displayed"]).onDismiss(any()) }
    }

    @Test
    public fun testConcurrentAddPending(): TestResult = runTest {
        val count = 100

        withContext(Dispatchers.Default) {
            (0 until count).map { i ->
                launch { addPending("banner-$i", i) }
            }.joinAll()
        }

        assertEquals(count, BannerViewManager.allPending().first().size)
    }

    private fun addPending(instanceId: String, priority: Int) {
        val listener = mockk<ThomasListenerInterface>(relaxed = true)
        val args = mockk<DisplayArgs>()
        every { args.listener } returns listener
        synchronized(listeners) { listeners[instanceId] = listener }

        BannerViewManager.addPending(
            viewInstanceId = instanceId,
            priority = priority,
            layoutInfoProvider = { mockk() },
            displayArgsProvider = { args },
        )
    }
}

package com.urbanairship.embedded

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import app.cash.turbine.test
import com.urbanairship.embedded.AirshipEmbeddedSelection
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Job
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class EmbeddedViewManagerTest {
    private val testEmbeddedId = UUID.randomUUID().toString()

    @After
    public fun teardown() {
        EmbeddedViewManager.dismissAll(testEmbeddedId)
    }

    // https://github.com/cashapp/turbine/issues/92
    // Turbine has an issue with SharedFlow or WhileSubscribed, using a
    // job to cancel the test seems to fix it.

    @Test
    public fun testPriorityDisplayOrder(): TestResult = runTest {
        val job = Job()
        EmbeddedViewManager.displayRequests(testEmbeddedId, scope = this + job).test {
            addPending("low priority", 100)
            assertEquals("low priority", awaitItem().next?.viewInstanceId);
            addPending("medium priority", 0)
            addPending("high priority", -100)
            cancelAndIgnoreRemainingEvents()
        }

        EmbeddedViewManager.displayRequests(testEmbeddedId, scope = this + job).test {
            assertEquals("low priority", awaitItem().next?.viewInstanceId);
            EmbeddedViewManager.dismiss(testEmbeddedId, "low priority")
            assertEquals("high priority", awaitItem().next?.viewInstanceId);
            cancelAndIgnoreRemainingEvents()
        }
        job.cancel()
    }

    @Test
    public fun testPriorityDisplayOrderBeforeSubscribe(): TestResult = runTest {
        addPending("low priority", 100)
        addPending("high priority", -100)
        addPending("medium priority", 0)


        val job = Job()
        EmbeddedViewManager.displayRequests(testEmbeddedId, scope = this + job).test {
            assertEquals("high priority", awaitItem().next?.viewInstanceId);
            EmbeddedViewManager.dismiss(testEmbeddedId, "high priority")

            assertEquals("medium priority", awaitItem().next?.viewInstanceId);
            EmbeddedViewManager.dismiss(testEmbeddedId, "medium priority")

            addPending("low priority", 0)
            cancelAndIgnoreRemainingEvents()
        }

        job.cancel()
    }

    @Test
    public fun testDisplayRequestsCustomComparator(): TestResult = runTest {
        addPending("low priority", 100)
        addPending("medium priority", 0)

        val job = Job()

        // Custom one does reverse priority order
        EmbeddedViewManager.displayRequests(
            testEmbeddedId,
            selection = AirshipEmbeddedSelection.ByComparator(compareByDescending { it.priority }),
            scope = this + job
        ).test {
            assertEquals("low priority", awaitItem().next?.viewInstanceId);
            cancelAndIgnoreRemainingEvents()
        }

        // Make sure standard sort still works
        EmbeddedViewManager.displayRequests(testEmbeddedId, scope = this + job).test {
            assertEquals("medium priority", awaitItem().next?.viewInstanceId);
            cancelAndIgnoreRemainingEvents()
        }
        addPending("high priority", -100)
        EmbeddedViewManager.displayRequests(testEmbeddedId, scope = this + job).test {
            assertEquals("medium priority", awaitItem().next?.viewInstanceId);
            cancelAndIgnoreRemainingEvents()
        }


        job.cancel()
    }

    @Test
    public fun testInstanceSelection(): TestResult = runTest {
        addPending("instance-a", 0)
        addPending("instance-b", 0)
        addPending("instance-c", 0)

        val job = Job()

        // Selecting by instance ID shows only that instance regardless of insertion order.
        EmbeddedViewManager.displayRequests(
            testEmbeddedId,
            selection = AirshipEmbeddedSelection.ByInstanceId("instance-b"),
            scope = this + job
        ).test {
            assertEquals("instance-b", awaitItem().next?.viewInstanceId)
            cancelAndIgnoreRemainingEvents()
        }

        // When the targeted instance is absent, next is null but the full list is still returned.
        EmbeddedViewManager.displayRequests(
            testEmbeddedId,
            selection = AirshipEmbeddedSelection.ByInstanceId("instance-missing"),
            scope = this + job
        ).test {
            val result = awaitItem()
            assertEquals(null, result.next?.viewInstanceId)
            assertEquals(3, result.list.size)
            cancelAndIgnoreRemainingEvents()
        }

        // Dismissing the targeted instance causes next to become null.
        EmbeddedViewManager.displayRequests(
            testEmbeddedId,
            selection = AirshipEmbeddedSelection.ByInstanceId("instance-a"),
            scope = this + job
        ).test {
            assertEquals("instance-a", awaitItem().next?.viewInstanceId)
            EmbeddedViewManager.dismiss(testEmbeddedId, "instance-a")
            assertEquals(null, awaitItem().next?.viewInstanceId)
            cancelAndIgnoreRemainingEvents()
        }

        job.cancel()
    }

    private fun addPending(instanceId: String, priority: Int) {
        EmbeddedViewManager.addPending(
            embeddedViewId = testEmbeddedId,
            viewInstanceId = instanceId,
            priority = priority,
            layoutInfoProvider = { mockk() },
            displayArgsProvider = { mockk() },
        )
    }
}

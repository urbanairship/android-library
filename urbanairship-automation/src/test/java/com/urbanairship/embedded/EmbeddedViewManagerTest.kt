package com.urbanairship.embedded

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import app.cash.turbine.test
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
            comparator = compareByDescending { it.priority },
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

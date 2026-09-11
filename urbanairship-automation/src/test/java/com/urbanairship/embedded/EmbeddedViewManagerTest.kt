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
        addPending("medium priority", 0)
        addPending("low priority", 100)

        val job = Job()

        // Custom one does reverse priority order. The pending list is added lowest-priority
        // first, so insertion order alone would answer "medium priority" here — this passes
        // only if the comparator sees the requests' real priorities.
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

    /**
     * An ordered allow-list: the earliest named instance that is pending wins, and pending
     * content not named is excluded rather than ordered last.
     */
    @Test
    public fun testInstanceSelection(): TestResult = runTest {
        addPending("instance-a", 0)
        addPending("instance-b", 0)
        addPending("instance-c", 0)

        val job = Job()

        // Preference order, not arrival order: b is named first even though a arrived first.
        EmbeddedViewManager.displayRequests(
            testEmbeddedId,
            selection = AirshipEmbeddedSelection.ByInstanceId(listOf("instance-b", "instance-a")),
            scope = this + job
        ).test {
            val result = awaitItem()
            assertEquals("instance-b", result.next?.viewInstanceId)
            // instance-c isn't named, so it isn't in the list at all.
            assertEquals(
                listOf("instance-b", "instance-a"),
                result.list.map { it.viewInstanceId }
            )
            cancelAndIgnoreRemainingEvents()
        }

        // The earliest *pending* entry wins, skipping ones that aren't pending.
        EmbeddedViewManager.displayRequests(
            testEmbeddedId,
            selection = AirshipEmbeddedSelection.ByInstanceId(
                listOf("instance-missing", "instance-c")
            ),
            scope = this + job
        ).test {
            assertEquals("instance-c", awaitItem().next?.viewInstanceId)
            cancelAndIgnoreRemainingEvents()
        }

        // None of them pending: nothing to show, and nothing in the list either.
        EmbeddedViewManager.displayRequests(
            testEmbeddedId,
            selection = AirshipEmbeddedSelection.ByInstanceId("instance-missing"),
            scope = this + job
        ).test {
            val result = awaitItem()
            assertEquals(null, result.next?.viewInstanceId)
            assertEquals(0, result.list.size)
            cancelAndIgnoreRemainingEvents()
        }

        // Dismissing the targeted instance falls to the next named one that is pending.
        EmbeddedViewManager.displayRequests(
            testEmbeddedId,
            selection = AirshipEmbeddedSelection.ByInstanceId(
                listOf("instance-a", "instance-c")
            ),
            scope = this + job
        ).test {
            assertEquals("instance-a", awaitItem().next?.viewInstanceId)
            EmbeddedViewManager.dismiss(testEmbeddedId, "instance-a")
            assertEquals("instance-c", awaitItem().next?.viewInstanceId)
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

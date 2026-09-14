/* Copyright Airship and Contributors */
package com.urbanairship.embedded

import app.cash.turbine.test
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.embedded.ai.EmbeddedAiSelector
import com.urbanairship.embedded.ai.EmbeddedSelectionRequest
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The AI branch emits twice — placeholder, then answer — because ranking is a round trip and
 * the view can't sit blank while it happens. Anything the model can't answer falls through to
 * the configured fallback, so an absent model never leaves an empty view, and a model that is
 * never asked shows no placeholder at all.
 */
@RunWith(AndroidJUnit4::class)
public class EmbeddedAiSelectionDisplayTest {

    private val embeddedId = UUID.randomUUID().toString()
    private val otherEmbeddedId = UUID.randomUUID().toString()
    private val selector = FakeSelector()

    private val selection = AirshipEmbeddedSelection.ByAi(
        config = AirshipEmbeddedSelection.ByAi.Config(prompt = "Prefer urgent offers"),
        fallback = AirshipEmbeddedSelection.ByAi.Fallback.Priority
    )

    @After
    public fun teardown() {
        EmbeddedViewManager.dismissAll(embeddedId)
        EmbeddedViewManager.dismissAll(otherEmbeddedId)
        EmbeddedViewManager.aiSelector = null
    }

    @Test
    public fun testPlaceholderThenTheModelsOrder(): TestResult = runTest {
        EmbeddedViewManager.aiSelector = selector
        selector.ranking = listOf("b", "a")
        addPending("a", priority = 0)
        addPending("b", priority = 10)

        val job = Job()
        EmbeddedViewManager.displayRequests(embeddedId, selection, this + job).test {
            // Placeholder while the model decides — the pending list is still reported.
            val placeholder = awaitItem()
            assertNull(placeholder.next)
            assertEquals(2, placeholder.list.size)

            val ranked = awaitItem()
            assertEquals("b", ranked.next?.viewInstanceId)
            assertEquals(listOf("b", "a"), ranked.list.map { it.viewInstanceId })

            cancelAndIgnoreRemainingEvents()
        }
        job.cancel()
    }

    @Test
    public fun testNoOpinionUsesTheFallback(): TestResult = runTest {
        EmbeddedViewManager.aiSelector = selector
        selector.ranking = null
        addPending("a", priority = 10)
        addPending("b", priority = 0)

        val job = Job()
        EmbeddedViewManager.displayRequests(embeddedId, selection, this + job).test {
            assertNull(awaitItem().next)
            // Priority fallback: lowest value wins.
            assertEquals("b", awaitItem().next?.viewInstanceId)
            cancelAndIgnoreRemainingEvents()
        }
        job.cancel()
    }

    @Test
    public fun testUnavailableModelNeverAsksAndFallsBack(): TestResult = runTest {
        EmbeddedViewManager.aiSelector = selector.apply { available = false }
        addPending("a", priority = 10)
        addPending("b", priority = 0)

        val job = Job()
        EmbeddedViewManager.displayRequests(embeddedId, selection, this + job).test {
            // Straight to the fallback: availability is synchronous, so there is no round
            // trip to show a placeholder through.
            assertEquals("b", awaitItem().next?.viewInstanceId)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        job.cancel()
        assertNull(selector.request)
    }

    @Test
    public fun testSingleCandidateIsNotWorthAsking(): TestResult = runTest {
        EmbeddedViewManager.aiSelector = selector
        selector.ranking = listOf("a")
        addPending("a", priority = 0, description = "Spring sale on cat trees")

        val job = Job()
        EmbeddedViewManager.displayRequests(embeddedId, selection, this + job).test {
            assertEquals("a", awaitItem().next?.viewInstanceId)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        job.cancel()
        assertEquals(0, selector.rankCount)
    }

    @Test
    public fun testAnotherViewsPendingChangeLeavesThisOneAlone(): TestResult = runTest {
        EmbeddedViewManager.aiSelector = selector
        selector.ranking = listOf("b", "a")
        addPending("a", priority = 0)
        addPending("b", priority = 10)

        val job = Job()
        EmbeddedViewManager.displayRequests(embeddedId, selection, this + job).test {
            assertNull(awaitItem().next)
            assertEquals("b", awaitItem().next?.viewInstanceId)

            EmbeddedViewManager.addPending(
                embeddedViewId = otherEmbeddedId,
                viewInstanceId = "other",
                priority = 0,
                layoutInfoProvider = { null },
                displayArgsProvider = { mockk() }
            )

            // No placeholder, no second ask: the displayed content is not torn down because
            // an unrelated embedded view gained an instance.
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        job.cancel()
        assertEquals(1, selector.rankCount)
    }

    @Test
    public fun testDismissalDoesNotReask(): TestResult = runTest {
        EmbeddedViewManager.aiSelector = selector
        selector.ranking = listOf("b", "a")
        addPending("a", priority = 0)
        addPending("b", priority = 10)

        val job = Job()
        EmbeddedViewManager.displayRequests(embeddedId, selection, this + job).test {
            assertNull(awaitItem().next)
            assertEquals("b", awaitItem().next?.viewInstanceId)

            EmbeddedViewManager.dismiss(embeddedId, "a")

            // The order drops what left, but the set never grew, so there is nothing new to
            // rank.
            assertEquals(listOf("b"), awaitItem().list.map { it.viewInstanceId })
            cancelAndIgnoreRemainingEvents()
        }
        job.cancel()
        assertEquals(1, selector.rankCount)
    }

    /**
     * A change to the pending set cancels the ranking it arrived during, so the placeholder
     * on screen has nothing left to answer it unless the cancelled ask is reissued.
     */
    @Test
    public fun testARemovalDuringRankingReasks(): TestResult = runTest {
        EmbeddedViewManager.aiSelector = selector
        selector.gate = CompletableDeferred()
        selector.ranking = listOf("b", "a")
        addPending("a", priority = 0)
        addPending("b", priority = 10)
        addPending("c", priority = 20)

        val job = Job()
        EmbeddedViewManager.displayRequests(embeddedId, selection, this + job).test {
            assertEquals(3, awaitItem().list.size)

            // Cancels the in-flight ranking without adding anything to rank.
            EmbeddedViewManager.dismiss(embeddedId, "c")
            selector.gate?.complete(Unit)

            // Still the placeholder, now over the shrunken list.
            val stillWaiting = awaitItem()
            assertNull(stillWaiting.next)
            assertEquals(2, stillWaiting.list.size)

            val ranked = awaitItem()
            assertEquals("b", ranked.next?.viewInstanceId)
            assertEquals(listOf("b", "a"), ranked.list.map { it.viewInstanceId })
            cancelAndIgnoreRemainingEvents()
        }
        job.cancel()
        assertEquals(2, selector.rankCount)
    }

    @Test
    public fun testArrivalDoesNotDisplaceWhatIsOnScreen(): TestResult = runTest {
        EmbeddedViewManager.aiSelector = selector
        selector.ranking = listOf("b", "a")
        addPending("a", priority = 0)
        addPending("b", priority = 10)

        val job = Job()
        EmbeddedViewManager.displayRequests(embeddedId, selection, this + job).test {
            assertNull(awaitItem().next)
            assertEquals("b", awaitItem().next?.viewInstanceId)

            selector.ranking = listOf("c", "b", "a")
            addPending("c", priority = 20)

            // Re-ranked with "c" on top, but "b" is already being looked at, so the arrival
            // joins the end rather than replacing it.
            val after = awaitItem()
            assertEquals("b", after.next?.viewInstanceId)
            assertEquals(listOf("b", "a", "c"), after.list.map { it.viewInstanceId })
            cancelAndIgnoreRemainingEvents()
        }
        job.cancel()
    }

    @Test
    public fun testArrivalDisplacesWhenInterruptionsAreAllowed(): TestResult = runTest {
        val interrupting = AirshipEmbeddedSelection.ByAi(
            config = AirshipEmbeddedSelection.ByAi.Config(
                prompt = "Prefer urgent offers",
                allowDisplayInterruptions = true
            ),
            fallback = AirshipEmbeddedSelection.ByAi.Fallback.Priority
        )

        EmbeddedViewManager.aiSelector = selector
        selector.ranking = listOf("b", "a")
        addPending("a", priority = 0)
        addPending("b", priority = 10)

        val job = Job()
        EmbeddedViewManager.displayRequests(embeddedId, interrupting, this + job).test {
            assertNull(awaitItem().next)
            assertEquals("b", awaitItem().next?.viewInstanceId)

            selector.ranking = listOf("c", "b", "a")
            addPending("c", priority = 20)

            val after = awaitItem()
            assertEquals("c", after.next?.viewInstanceId)
            assertEquals(listOf("c", "b", "a"), after.list.map { it.viewInstanceId })
            cancelAndIgnoreRemainingEvents()
        }
        job.cancel()
    }

    @Test
    public fun testCandidatesCarryWhatTheLayoutSaysAboutThem(): TestResult = runTest {
        EmbeddedViewManager.aiSelector = selector
        selector.ranking = listOf("a", "b")
        addPending("a", priority = 0, description = "Spring sale on cat trees")
        addPending("b", priority = 10, description = "Dog grooming week")

        val job = Job()
        EmbeddedViewManager.displayRequests(embeddedId, selection, this + job).test {
            awaitItem()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        job.cancel()

        assertEquals(embeddedId, selector.request?.embeddedId)
        assertEquals("Prefer urgent offers", selector.request?.prompt)
        assertEquals(
            listOf("Spring sale on cat trees", "Dog grooming week"),
            selector.request?.candidates?.map { it.contentDescription }
        )
    }

    private fun addPending(instanceId: String, priority: Int, description: String? = null) {
        val layout = description?.let {
            LayoutInfo(
                JsonValue.parseString(
                    """
                    {
                        "version": 1,
                        "presentation": {
                            "type": "embedded",
                            "embedded_id": "$embeddedId",
                            "default_placement": { "size": { "width": "100%", "height": "auto" } }
                        },
                        "view": { "type": "empty_view" },
                        "content_description": { "description": "$it" }
                    }
                    """
                ).requireMap()
            )
        }

        EmbeddedViewManager.addPending(
            embeddedViewId = embeddedId,
            viewInstanceId = instanceId,
            priority = priority,
            layoutInfoProvider = { layout },
            displayArgsProvider = { mockk() }
        )
    }

    private class FakeSelector : EmbeddedAiSelector {
        var available: Boolean = true
        var ranking: List<String>? = null

        /** Holds `rank` open so a test can change the pending set mid-flight. */
        var gate: CompletableDeferred<Unit>? = null

        var request: EmbeddedSelectionRequest? = null
            private set

        var rankCount: Int = 0
            private set

        override val isAvailable: Boolean
            get() = available

        override suspend fun rank(request: EmbeddedSelectionRequest): List<String>? {
            this.request = request
            rankCount += 1
            gate?.await()
            return ranking
        }
    }
}

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
 * the configured fallback, so an absent model never leaves an empty view.
 */
@RunWith(AndroidJUnit4::class)
public class EmbeddedAiSelectionDisplayTest {

    private val embeddedId = UUID.randomUUID().toString()
    private val selector = FakeSelector()

    private val selection = AirshipEmbeddedSelection.ByAi(
        config = AirshipEmbeddedSelection.ByAi.Config(prompt = "Prefer urgent offers"),
        fallback = AirshipEmbeddedSelection.ByAi.Fallback.Priority
    )

    @After
    public fun teardown() {
        EmbeddedViewManager.dismissAll(embeddedId)
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
            assertNull(awaitItem().next)
            assertEquals("b", awaitItem().next?.viewInstanceId)
            cancelAndIgnoreRemainingEvents()
        }
        job.cancel()
        assertNull(selector.request)
    }

    @Test
    public fun testCandidatesCarryWhatTheLayoutSaysAboutThem(): TestResult = runTest {
        EmbeddedViewManager.aiSelector = selector
        selector.ranking = listOf("a")
        addPending("a", priority = 0, description = "Spring sale on cat trees")

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
            "Spring sale on cat trees",
            selector.request?.candidates?.single()?.contentDescription
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

        var request: EmbeddedSelectionRequest? = null
            private set

        override val isAvailable: Boolean
            get() = available

        override suspend fun rank(request: EmbeddedSelectionRequest): List<String>? {
            this.request = request
            return ranking
        }
    }
}

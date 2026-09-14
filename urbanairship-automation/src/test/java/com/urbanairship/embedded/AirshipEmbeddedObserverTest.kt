/* Copyright Airship and Contributors */
package com.urbanairship.embedded

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The observer reports the same info every selection sees, rather than minting a thinner one
 * of its own — so a filter on priority or on what the content is about can actually work.
 */
@RunWith(AndroidJUnit4::class)
public class AirshipEmbeddedObserverTest {

    private val embeddedId = UUID.randomUUID().toString()

    @After
    public fun teardown() {
        EmbeddedViewManager.dismissAll(embeddedId)
    }

    @Test
    public fun testReportedInfoCarriesPriorityAndDescription(): TestResult = runTest {
        addPending("a", priority = 7, description = "Spring sale on cat trees")
        addPending("b", priority = 3)

        val observer = AirshipEmbeddedObserver(
            filter = { it.embeddedId == embeddedId },
            manager = EmbeddedViewManager,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        observer.embeddedViewInfoFlow.test {
            val infos = awaitItem().associateBy { it.instanceId }

            assertEquals(7, infos.getValue("a").priority)
            assertEquals(3, infos.getValue("b").priority)
            assertEquals("Spring sale on cat trees", infos.getValue("a").contentDescription)
            assertEquals(null, infos.getValue("b").contentDescription)
            assertEquals(jsonMapOf("k" to "v"), infos.getValue("a").extras)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun testFilterCanSelectOnPriority(): TestResult = runTest {
        addPending("a", priority = 7)
        addPending("b", priority = 3)

        val observer = AirshipEmbeddedObserver(
            filter = { it.embeddedId == embeddedId && it.priority < 5 },
            manager = EmbeddedViewManager,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        observer.embeddedViewInfoFlow.test {
            assertEquals(listOf("b"), awaitItem().map { it.instanceId })
            cancelAndIgnoreRemainingEvents()
        }
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
            extras = jsonMapOf("k" to "v"),
            layoutInfoProvider = { layout },
            displayArgsProvider = { mockk() }
        )
    }
}

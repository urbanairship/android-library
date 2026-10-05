/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.ui

import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.ModalPresentation
import com.urbanairship.android.layout.environment.EnvironmentFrame
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.android.layout.util.WindowPixels
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Below API 28 a modal's frame depends on its placement, so it only settles on a placement its own frame chooses. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
public class ModalPlacementResolverTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    public fun setUp() {
        mockkObject(ResourceUtils)
        every { ResourceUtils.getWindowPixels(any(), any()) } returns WindowPixels(400, 800, 400, 700)
    }

    @After
    public fun tearDown() {
        unmockkObject(ResourceUtils)
    }

    @Test
    public fun testAPlacementWhoseFrameChoosesAnotherIsUnstableBothWays() {
        // Tall chooses the safe placement, whose safe frame is short; short chooses the edge-to-edge one.
        val presentation = presentation(tall = SAFE, short = EDGE_TO_EDGE)
        val resolver = resolver(presentation)
        val safe = presentation.defaultPlacement
        val edgeToEdge = presentation.placementSelectors!!.single().placement

        assertEquals(EnvironmentFrame(400, 700), resolver.frameFor(safe))
        assertEquals(EnvironmentFrame(400, 800), resolver.frameFor(edgeToEdge))
        assertFalse(resolver.isStable(safe))
        assertFalse(resolver.isStable(edgeToEdge))
    }

    @Test
    public fun testOpensWithTheSafeWindowsChoiceWhenTheWholeWindowsWouldNotFillIt() {
        val presentation = presentation(tall = SAFE, short = EDGE_TO_EDGE)
        val placement = resolver(presentation).initialPlacement()
        assertSame(presentation.placementSelectors!!.single().placement, placement)
    }

    @Test
    public fun testOpensWithTheWholeWindowsChoiceWhenItFillsTheWholeWindow() {
        val presentation = presentation(tall = EDGE_TO_EDGE, short = SAFE)
        val resolver = resolver(presentation)
        val placement = resolver.initialPlacement()
        assertSame(presentation.defaultPlacement, placement)
        assertTrue(resolver.isStable(placement))
    }

    @Test
    @Config(sdk = [28])
    public fun testFromApi28EveryPlacementFillsTheWholeWindow() {
        val presentation = presentation(tall = SAFE, short = EDGE_TO_EDGE)
        val resolver = resolver(presentation)
        assertEquals(EnvironmentFrame(400, 800), resolver.frameFor(presentation.defaultPlacement))
        assertTrue(resolver.isStable(presentation.defaultPlacement))
    }

    private fun resolver(presentation: ModalPresentation) =
        ModalPlacementResolver(context, presentation, View(context)) { frame -> conditionsFor(frame) }

    /** Chooses `height` tall for a frame taller than the safe window, else short. */
    private fun conditionsFor(frame: EnvironmentFrame): JsonMap =
        jsonMapOf("height" to if ((frame.height ?: 0) > 750) "tall" else "short")

    /** A modal showing [tall] by default and [short] when the `height` condition is short. */
    private fun presentation(tall: String, short: String): ModalPresentation {
        val scene = """
            {"version": 1,
             "presentation": {"type": "modal", "default_placement": $tall,
               "placement_selectors": [{"environment_conditions": {"height": "short"}, "placement": $short}]},
             "view": {"type": "empty_view"}}
        """.trimIndent()
        return LayoutInfo(JsonValue.parseString(scene).optMap()).presentation as ModalPresentation
    }

    private companion object {
        const val SAFE = """{"size": {"width": "100%", "height": "100%"}}"""
        const val EDGE_TO_EDGE = """{"size": {"width": "100%", "height": "100%"}, "ignore_safe_area": true}"""
    }
}

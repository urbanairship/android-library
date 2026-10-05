/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.ui

import android.app.Activity
import android.widget.EditText
import android.view.Gravity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.urbanairship.Airship
import com.urbanairship.android.layout.view.StackImageView
import com.urbanairship.android.layout.AirshipCustomViewManager
import com.urbanairship.android.layout.EmbeddedPresentation
import com.urbanairship.android.layout.property.EmbeddedPlacement
import com.urbanairship.android.layout.widget.ConstrainedFrameLayout
import com.urbanairship.android.layout.view.ModalView
import com.urbanairship.android.layout.environment.EnvironmentFrame
import com.urbanairship.android.layout.ModalPresentation
import com.urbanairship.android.layout.BannerPresentation
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.images.ImageLoader
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A scene's environment conditions reach its colors and placement, and follow its frame. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "mdpi")
public class EnvironmentConditionsViewTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val viewEnv: ViewEnvironment = mockk(relaxed = true) {
        every { isIgnoringSafeAreas } returns false
        every { imageCache() } returns null
    }

    @Before
    public fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(testDispatcher.scheduler))
        mockkObject(Airship)
        every { Airship.imageLoader } returns mockk<ImageLoader>(relaxed = true)
    }

    @After
    public fun tearDown() {
        unmockkObject(Airship)
        Dispatchers.resetMain()
    }

    @Test
    public fun testAColorSelectorMatchesTheConditionTheFrameChose() {
        val (view, _) = embedded(width = 300)
        assertEquals(RED, labelColor(view))
    }

    @Test
    public fun testCrossingABreakpointRecolorsTheSameViewsInPlace() {
        val (view, host) = embedded(width = 300)
        val label = label(view)
        assertEquals(RED, label.currentTextColor)
        assertEquals(RED, (label.background as ColorDrawable).color)

        host.layoutParams = FrameLayout.LayoutParams(500, ViewGroup.LayoutParams.WRAP_CONTENT)
        settle()
        assertSame("the label isn't rebuilt", label, label(view))
        assertEquals(BLUE, label.currentTextColor)
        assertEquals(BLUE, (label.background as ColorDrawable).color)
    }

    @Test
    public fun testAPlacementSelectorMatchesTheConditionTooAndFollowsIt() {
        val (view, host) = embedded(width = 300)
        assertEquals("compact selects the narrow placement", 0.5f, view.placement.size.width.getFloat())

        host.layoutParams = FrameLayout.LayoutParams(500, ViewGroup.LayoutParams.WRAP_CONTENT)
        settle()
        assertEquals(1f, view.placement.size.width.getFloat())
    }

    @Test
    public fun testStopsSwappingPlacementsWhenTheHostSizesTheViewByItsPlacement() {
        val (view, host) = embedded(width = 500, scene = CYCLING)
        var moves = 0
        // As the automation hosts do: each placement's width percent of the host, filled.
        val sizeByPlacement = { placement: EmbeddedPlacement ->
            view.layoutParams = FrameLayout.LayoutParams((500 * placement.size.width.getFloat()).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        view.onPlacementChanged = { moves++; sizeByPlacement(it) }
        sizeByPlacement(view.placement)
        repeat(4) { settle() }
        assertTrue("$moves moves", moves in 1..2)
    }

    @Test
    public fun testRecolorsInPlaceWithoutMovingTheSceneWhenThePlacementStaysTheSame() {
        val (view, host) = embedded(width = 300, scene = scene(LABEL))
        val label = label(view)
        val parent = label.parent

        cross(host)
        assertSame("still in the same container", parent, label.parent)
        assertTrue(label.isAttachedToWindow)
        assertEquals(BLUE, label.currentTextColor)
    }

    @Test
    public fun testAMultilineTextInputKeepsItsTopAlignmentWhenRecolored() {
        val input = """
            {"type": "form_controller", "identifier": "form", "submit": "submit_event",
             "view": {"type": "text_input", "identifier": "input", "input_type": "text_multiline",
                      "text_appearance": {"font_size": 14, "color": $COLOR}}}
        """.trimIndent()
        val (view, host) = embedded(width = 300, scene = scene(input))
        val field = view.descendants().filterIsInstance<EditText>().single()
        assertEquals(RED, field.currentTextColor)

        cross(host)
        assertEquals(BLUE, field.currentTextColor)
        assertEquals(Gravity.TOP, field.gravity and Gravity.VERTICAL_GRAVITY_MASK)
    }

    @Test
    public fun testAStackImageKeepsItsOwnBackgroundOverItsRecoloredShape() {
        val stack = """
            {"type": "stack_image_view", "identifier": "stack", "background_color": $COLOR,
             "items": [{"type": "shape", "shape": {"type": "rectangle", "color": $COLOR}}]}
        """.trimIndent()
        val (view, host) = embedded(width = 300, scene = scene(stack))
        val image = view.descendants().filterIsInstance<StackImageView>().single()
        assertTrue("the shape with the view's background over it", image.background is LayerDrawable)

        cross(host)
        assertTrue("still both, not the shape alone: ${image.background}", image.background is LayerDrawable)
    }

    @Test
    public fun testACustomViewHandlerStillGetsTheContextItWasGiven() {
        var given: Context? = null
        AirshipCustomViewManager.register("probe") { context, _ -> given = context; View(context) }
        try {
            embedded(width = 300, scene = scene("""{"type": "custom_view", "name": "probe"}"""))
            assertTrue("an activity, as before: $given", given is Activity)
        } finally {
            AirshipCustomViewManager.unregister("probe")
        }
    }

    @Test
    public fun testABannerMovesTheSameContentIntoAFrameForThePlacementTheConditionSelects() {
        val layout = LayoutInfo(JsonValue.parseString(BANNER).optMap())
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val model = LayoutViewModel().getOrCreateModel(layout.view, environment(layout))
        val banner = ThomasBannerView(activity, model, layout.presentation as BannerPresentation, viewEnv)
        val host = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(300, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(banner, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        activity.setContentView(FrameLayout(activity).apply { addView(host) })
        settle()
        val label = label(banner)
        val compactFrame = banner.descendants().filterIsInstance<ConstrainedFrameLayout>().single()
        assertEquals("compact selects the red placement", RED, (compactFrame.background as ColorDrawable).color)

        cross(host)
        val expandedFrame = banner.descendants().filterIsInstance<ConstrainedFrameLayout>().single()
        assertTrue("a new frame for the new placement", expandedFrame !== compactFrame)
        assertEquals(BLUE, (expandedFrame.background as ColorDrawable).color)
        assertSame("the same content, moved", label, label(banner))
        assertSame(expandedFrame, label.parent)
    }

    @Test
    public fun testAModalAsksForARecreateOnceWhenItsFrameSelectsAnotherPlacement() {
        var recreates = 0
        val (_, host) = modal(seedFrame = false) { recreates++ }
        // Seeded without a frame, it chose the expanded placement; its first layout reports 300 wide.
        assertEquals(1, recreates)

        host.requestLayout()
        settle()
        assertEquals("only once", 1, recreates)
    }

    @Test
    public fun testAModalSeededWithTheFrameItFillsNeverRecreates() {
        var recreates = 0
        modal(seedFrame = true) { recreates++ }
        assertEquals(0, recreates)
    }

    /**
     * A modal in a host 300 wide, whose placement selector reads the frame's `size`, seeded with
     * that frame, as `ModalActivity` seeds it, when [seedFrame].
     */
    private fun modal(seedFrame: Boolean, onRecreate: () -> Unit): Pair<ModalView, FrameLayout> {
        val layout = LayoutInfo(JsonValue.parseString(MODAL).optMap())
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val environment = environment(layout)
        if (seedFrame) environment.seedEnvironment(activity.window.decorView, EnvironmentFrame(300, 600))
        val model = LayoutViewModel().getOrCreateModel(layout.view, environment)
        val view = ModalView(activity, model, layout.presentation as ModalPresentation, viewEnv).apply {
            onPlacementSuperseded = onRecreate
        }
        val host = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(300, 600)
            addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        activity.setContentView(FrameLayout(activity).apply { addView(host) })
        settle()
        return view to host
    }

    private fun environment(layout: LayoutInfo) = ModelEnvironment(
        layoutState = LayoutState.EMPTY,
        reporter = mockk(relaxUnitFun = true),
        actionsRunner = mockk(relaxUnitFun = true),
        displayTimer = mockk(relaxed = true),
        modelScope = testScope,
        environmentConditions = layout.environmentConditions,
        selectsOnEnvironmentConditions = layout.selectsOnEnvironmentConditions
    )

    /** Widens the host past the `size` breakpoint and settles. */
    private fun cross(host: FrameLayout) {
        host.layoutParams = FrameLayout.LayoutParams(500, ViewGroup.LayoutParams.WRAP_CONTENT)
        settle()
    }

    /** An embedded scene showing [view], with the frame's `size` condition and no placement selectors. */
    private fun scene(view: String) = """
        {"version": 1,
         "environment_state": {"conditions": {"size": [
           {"frame_width": {"at_most": 400}, "value": "compact"}, {"value": "expanded"}
         ]}},
         "presentation": {"type": "embedded", "embedded_id": "home",
           "default_placement": {"size": {"width": "100%", "height": "auto"}}},
         "view": $view}
    """.trimIndent()

    /** An embedded scene in a host [width] wide, whose `size` condition reads the frame's width. */
    private fun embedded(width: Int, scene: String = SCENE): Pair<ThomasEmbeddedView, FrameLayout> {
        val layout = LayoutInfo(JsonValue.parseString(scene).optMap())
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val environment = ModelEnvironment(
            layoutState = LayoutState.EMPTY,
            reporter = mockk(relaxUnitFun = true),
            actionsRunner = mockk(relaxUnitFun = true),
            displayTimer = mockk(relaxed = true),
            modelScope = testScope,
            environmentConditions = layout.environmentConditions,
            selectsOnEnvironmentConditions = layout.selectsOnEnvironmentConditions
        )
        val model = LayoutViewModel().getOrCreateModel(layout.view, environment)
        val view = ThomasEmbeddedView(activity, model, layout.presentation as EmbeddedPresentation, viewEnv, fillWidth = true)
        val host = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        activity.setContentView(FrameLayout(activity).apply { addView(host) })
        settle()
        return view to host
    }

    /** Runs the layouts the window schedules, the publishes they post and what those post. */
    private fun settle() {
        repeat(4) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
            testDispatcher.scheduler.advanceUntilIdle()
        }
    }

    private fun label(view: View): TextView = view.descendants().filterIsInstance<TextView>().single()

    private fun labelColor(view: View): Int = label(view).currentTextColor

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) (0 until childCount).forEach { yieldAll(getChildAt(it).descendants()) }
    }

    private companion object {
        const val RED = 0xFFFF0000.toInt()
        const val BLUE = 0xFF0000FF.toInt()

        /** Red while the `size` condition is compact, else blue. */
        val COLOR = """
            {"default": {"type": "hex", "hex": "#0000FF", "alpha": 1},
             "selectors": [{"environment_conditions": {"size": "compact"},
                            "color": {"type": "hex", "hex": "#FF0000", "alpha": 1}}]}
        """.trimIndent()

        /** The frame's `size` condition, compact up to 400 wide. */
        const val SIZE = """
            "environment_state": {"conditions": {"size": [
              {"frame_width": {"at_most": 400}, "value": "compact"}, {"value": "expanded"}
            ]}}
        """

        val BANNER = """
            {"version": 1, $SIZE,
             "presentation": {"type": "banner",
               "default_placement": {"size": {"width": "100%", "height": "auto"},
                 "position": {"horizontal": "center", "vertical": "top"},
                 "background_color": {"default": {"type": "hex", "hex": "#0000FF", "alpha": 1}}},
               "placement_selectors": [{"environment_conditions": {"size": "compact"},
                 "placement": {"size": {"width": "100%", "height": "auto"},
                   "position": {"horizontal": "center", "vertical": "bottom"},
                   "background_color": {"default": {"type": "hex", "hex": "#FF0000", "alpha": 1}}}}]},
             "view": {"type": "label", "text": "x",
               "text_appearance": {"font_size": 14, "color": {"default": {"type": "hex", "hex": "#000000", "alpha": 1}}}}}
        """.trimIndent()

        val MODAL = """
            {"version": 1, $SIZE,
             "presentation": {"type": "modal",
               "default_placement": {"size": {"width": "100%", "height": "100%"}},
               "placement_selectors": [{"environment_conditions": {"size": "compact"},
                 "placement": {"size": {"width": "90%", "height": "90%"}}}]},
             "view": {"type": "empty_view"}}
        """.trimIndent()

        /** Wide, the frame selects the narrow placement; narrow, the default wide one. */
        val CYCLING = """
            {"version": 1, $SIZE,
             "presentation": {"type": "embedded", "embedded_id": "home",
               "default_placement": {"size": {"width": "100%", "height": "auto"}},
               "placement_selectors": [{"environment_conditions": {"size": "expanded"},
                 "placement": {"size": {"width": "50%", "height": "auto"}}}]},
             "view": {"type": "label", "text": "x",
               "text_appearance": {"font_size": 14, "color": {"default": {"type": "hex", "hex": "#000000", "alpha": 1}}}}}
        """.trimIndent()

        val LABEL = """{"type": "label", "text": "x", "text_appearance": {"font_size": 14, "color": $COLOR}}"""

        val SCENE = """
            {"version": 1,
             "environment_state": {"conditions": {"size": [
               {"frame_width": {"at_most": 400}, "value": "compact"},
               {"value": "expanded"}
             ]}},
             "presentation": {"type": "embedded", "embedded_id": "home",
               "default_placement": {"size": {"width": "100%", "height": "auto"}},
               "placement_selectors": [{"environment_conditions": {"size": "compact"},
                 "placement": {"size": {"width": "50%", "height": "auto"}}}]},
             "view": {"type": "label", "text": "x",
               "background_color": {
                 "default": {"type": "hex", "hex": "#0000FF", "alpha": 1},
                 "selectors": [{"environment_conditions": {"size": "compact"},
                                "color": {"type": "hex", "hex": "#FF0000", "alpha": 1}}]},
               "text_appearance": {"font_size": 14, "color": {
                 "default": {"type": "hex", "hex": "#0000FF", "alpha": 1},
                 "selectors": [{"environment_conditions": {"size": "compact"},
                                "color": {"type": "hex", "hex": "#FF0000", "alpha": 1}}]}}}}
        """.trimIndent()
    }
}

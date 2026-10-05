/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.ui

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.urbanairship.Airship
import com.urbanairship.android.layout.EmbeddedPresentation
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

    /** An embedded scene in a host [width] wide, whose `size` condition reads the frame's width. */
    private fun embedded(width: Int): Pair<ThomasEmbeddedView, FrameLayout> {
        val layout = LayoutInfo(JsonValue.parseString(SCENE).optMap())
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val environment = ModelEnvironment(
            layoutState = LayoutState.EMPTY,
            reporter = mockk(relaxUnitFun = true),
            actionsRunner = mockk(relaxUnitFun = true),
            displayTimer = mockk(relaxed = true),
            modelScope = testScope,
            environmentConditions = layout.environmentConditions,
            viewsSelectOnEnvironmentConditions = layout.viewSelectsOnEnvironmentConditions
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

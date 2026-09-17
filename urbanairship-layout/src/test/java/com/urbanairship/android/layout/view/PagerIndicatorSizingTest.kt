/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.Airship
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.ui.LayoutViewModel
import com.urbanairship.images.ImageLoader
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * What a row of page dots does when the buttons beside it have taken most of the width.
 *
 * The dots are the only thing in a footer that can give: a stated length reports what it was told
 * to be however little room it is handed, and a row that can't fit its content drops whatever
 * isn't one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class PagerIndicatorSizingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val mockEnv: ModelEnvironment = mockk(relaxed = true) {
        every { layoutState } returns LayoutState.EMPTY
        every { layoutEvents } returns emptyFlow()
        every { modelScope } returns testScope
        // The factory hands every model a copy of the environment carrying its controllers' state.
        // Relaxed, that copy has a pager the indicator then reads a mock page out of.
        every { withState(any()) } returns this@mockk
    }
    private val viewEnv: ViewEnvironment = mockk(relaxed = true) {
        every { isIgnoringSafeAreas } returns false
        every { imageCache() } returns null
    }

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(Airship)
        every { Airship.imageLoader } returns mockk<ImageLoader>(relaxed = true)
    }

    @After
    public fun tearDown() {
        unmockkObject(Airship)
        Dispatchers.resetMain()
    }

    /** Dots narrow to the room the buttons left, rather than being dropped for asking too much. */
    @Test
    public fun testDotsNarrowToWhatTheButtonsLeft() {
        val row = footer(pages = PAGES)
        row.measure(exactly(ROW), exactly(HEIGHT))

        val dots = requireNotNull(findIndicator(row)) { "no indicator" }
        assertTrue("dots have a width: ${dots.measuredWidth}", dots.measuredWidth > 0)

        val total = (0 until row.childCount).sumOf { row.getChildAt(it).measuredWidth }
        assertTrue("the row fits: $total", total <= ROW)
    }

    /** They stay square, so the row stays a row of dots rather than a row of bars. */
    @Test
    public fun testDotsStaySquare() {
        val row = footer(pages = PAGES)
        row.measure(exactly(ROW), exactly(HEIGHT))

        val dots = requireNotNull(findIndicator(row)) { "no indicator" }
        for (i in 0 until dots.childCount) {
            val dot = dots.getChildAt(i)
            assertEquals("dot $i", dot.measuredHeight, dot.measuredWidth)
        }
    }

    /** With room to spare they keep the size they would have had. */
    @Test
    public fun testDotsKeepTheirSizeWhenTheyFit() {
        val row = footer(pages = 2)
        row.measure(exactly(ROW), exactly(HEIGHT))

        val dots = requireNotNull(findIndicator(row)) { "no indicator" }
        assertEquals("dot size", DEFAULT_DOT, dots.getChildAt(0).measuredWidth)
    }

    private fun exactly(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    private fun findIndicator(view: View): PagerIndicatorView? {
        if (view is PagerIndicatorView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findIndicator(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    /** A button, the dots, and a button, the way a pager footer is put together. */
    private fun footer(pages: Int): ViewGroup {
        val json = """
            {
              "type": "linear_layout",
              "direction": "horizontal",
              "items": [
                {"size": {"width": $BUTTON, "height": 32}, "view": {"type": "empty_view"}},
                {
                  "size": {"width": "auto", "height": "auto"},
                  "view": {
                    "type": "pager_indicator",
                    "spacing": $SPACING,
                    "bindings": {
                      "selected": {"shapes": [{"type": "ellipse", "scale": 0.5, "aspect_ratio": 1,
                        "color": {"default": {"type": "hex", "hex": "#000000", "alpha": 1}}}]},
                      "unselected": {"shapes": [{"type": "ellipse", "scale": 0.4, "aspect_ratio": 1,
                        "color": {"default": {"type": "hex", "hex": "#cccccc", "alpha": 1}}}]}
                    }
                  }
                },
                {"size": {"width": $BUTTON, "height": 32}, "view": {"type": "empty_view"}}
              ]
            }
        """.trimIndent()

        val info = ViewInfo.viewInfoFromJson(JsonValue.parseString(json).requireMap())
        val row = LayoutViewModel().getOrCreateModel(info, mockEnv)
            .createView(context, viewEnv, null) as ViewGroup

        Robolectric.buildActivity(Activity::class.java).setup().get().setContentView(row)
        // The dots come from the pager's state, which no pager is driving here.
        findIndicator(row)?.setCount(pages)

        return row
    }

    private companion object {
        private const val ROW = 386
        private const val HEIGHT = 48
        private const val BUTTON = 96
        private const val SPACING = 6
        private const val PAGES = 7

        /** [PagerIndicatorView.DEFAULT_DOT_SIZE_DP] at the test's density. */
        private const val DEFAULT_DOT = 32
    }
}

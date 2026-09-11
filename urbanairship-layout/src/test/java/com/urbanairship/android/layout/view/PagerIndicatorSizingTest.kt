/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.info.PagerIndicatorInfo
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.model.PagerIndicatorModel
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A dot draws into the bounds it is given and has no size of its own, so it takes the indicator's
 * height. An item that states `auto` leaves the indicator taking its height from the dots in turn.
 */
@RunWith(RobolectricTestRunner::class)
public class PagerIndicatorSizingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val info: PagerIndicatorInfo =
        ViewInfo.viewInfoFromJson(JsonValue.parseString(INDICATOR).requireMap()) as PagerIndicatorInfo

    private val model: PagerIndicatorModel = mockk(relaxed = true) {
        every { viewInfo } returns info
        every { announcePage } returns false
        every { getIndicatorViewId(any()) } answers { View.generateViewId() }
    }

    /** The bug: neither the row nor the dots supply a height, and both settled at nothing. */
    @Test
    public fun testAutoHeightDotsTakeTheDefault() {
        val view = indicator()
        view.measure(spec(PAGE, View.MeasureSpec.EXACTLY), spec(PAGE, View.MeasureSpec.AT_MOST))

        val dot = ResourceUtils.dpToPx(context, 32).toInt()
        assertEquals("indicator height", dot, view.measuredHeight)
        for (i in 0 until DOTS) {
            assertEquals("dot $i width", dot, view.getChildAt(i).measuredWidth)
            assertEquals("dot $i height", dot, view.getChildAt(i).measuredHeight)
        }
    }

    /** A stated height is the dot, so an author can still ask for smaller ones. */
    @Test
    public fun testStatedHeightSizesTheDots() {
        val view = indicator()
        view.measure(spec(PAGE, View.MeasureSpec.EXACTLY), spec(24, View.MeasureSpec.EXACTLY))

        assertEquals("indicator height", 24, view.measuredHeight)
        assertEquals("dot width", 24, view.getChildAt(0).measuredWidth)
    }

    private fun spec(size: Int, mode: Int) = View.MeasureSpec.makeMeasureSpec(size, mode)

    private fun indicator(): PagerIndicatorView =
        PagerIndicatorView(context, model).also { it.setCount(DOTS) }

    private companion object {
        private const val PAGE = 1000
        private const val DOTS = 6

        private val INDICATOR = """
            {
              "type": "pager_indicator",
              "spacing": 8,
              "bindings": {
                "selected": {
                  "shapes": [{"type": "ellipse", "scale": 1, "aspect_ratio": 1,
                              "color": {"default": {"type": "hex", "hex": "#2f5bea", "alpha": 1}}}]
                },
                "unselected": {
                  "shapes": [{"type": "ellipse", "scale": 1, "aspect_ratio": 1,
                              "color": {"default": {"type": "hex", "hex": "#5f6368", "alpha": 0.25}}}]
                }
              }
            }
        """.trimIndent()
    }
}

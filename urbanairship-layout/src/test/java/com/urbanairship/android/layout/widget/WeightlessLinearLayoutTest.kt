/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies that percent-sized children with margins are measured using iOS HStack/VStack-style
 * "equal slot" distribution: each percent child gets an equal share of the parent's length, and
 * its own margin is then subtracted from that share to produce its content size. Previously,
 * Android instead removed all percent-children's margins from the distributable space up front
 * and split the remainder evenly as *content*, which produced equal content sizes with additive
 * margins instead of equal slots.
 */
@RunWith(RobolectricTestRunner::class)
public class WeightlessLinearLayoutTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    public fun testHorizontalPercentChildrenWithMarginDistributeEqualSlots() {
        val layout = WeightlessLinearLayout(context)

        val margined = View(context)
        val plainA = View(context)
        val plainB = View(context)

        layout.addView(margined, percentWidthLayoutParams(marginEnd = 120))
        layout.addView(plainA, percentWidthLayoutParams())
        layout.addView(plainB, percentWidthLayoutParams())

        layout.measure(
            View.MeasureSpec.makeMeasureSpec(972, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST)
        )

        // Slots are equal (324px each); the margined child's content is its slot minus its own
        // margin (324 - 120 = 204), while the unmargined children keep their full slot.
        assertEquals(204, margined.measuredWidth)
        assertEquals(324, plainA.measuredWidth)
        assertEquals(324, plainB.measuredWidth)

        // Total footprint (content + margins) still exactly fills the parent.
        assertEquals(
            972,
            margined.measuredWidth + 120 + plainA.measuredWidth + plainB.measuredWidth
        )
    }

    @Test
    public fun testVerticalPercentChildrenWithMarginDistributeEqualSlots() {
        val layout = WeightlessLinearLayout(context)
        layout.setOrientation(WeightlessLinearLayout.OrientationMode.VERTICAL)

        val margined = View(context)
        val plainA = View(context)
        val plainB = View(context)

        layout.addView(margined, percentHeightLayoutParams(topMargin = 120))
        layout.addView(plainA, percentHeightLayoutParams())
        layout.addView(plainB, percentHeightLayoutParams())

        layout.measure(
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(972, View.MeasureSpec.EXACTLY)
        )

        assertEquals(204, margined.measuredHeight)
        assertEquals(324, plainA.measuredHeight)
        assertEquals(324, plainB.measuredHeight)

        assertEquals(
            972,
            margined.measuredHeight + 120 + plainA.measuredHeight + plainB.measuredHeight
        )
    }

    @Test
    public fun testHorizontalPercentChildrenWithNoMarginsSplitEvenly() {
        val layout = WeightlessLinearLayout(context)

        val a = View(context)
        val b = View(context)
        val c = View(context)

        layout.addView(a, percentWidthLayoutParams())
        layout.addView(b, percentWidthLayoutParams())
        layout.addView(c, percentWidthLayoutParams())

        layout.measure(
            View.MeasureSpec.makeMeasureSpec(972, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST)
        )

        // No margins in play: unaffected by the slot-distribution change, still an even split.
        assertEquals(324, a.measuredWidth)
        assertEquals(324, b.measuredWidth)
        assertEquals(324, c.measuredWidth)
    }

    @Test
    public fun testHorizontalPercentChildrenWithDifferentPercentsSplitByShare() {
        val layout = WeightlessLinearLayout(context)

        val quarter = View(context)
        val threeQuarter = View(context)

        // Added largest-first to confirm the internal sort (ascending by percent) drives
        // allocation rather than insertion order.
        layout.addView(threeQuarter, percentWidthLayoutParams(percent = 0.75f))
        layout.addView(quarter, percentWidthLayoutParams(percent = 0.25f))

        layout.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST)
        )

        // 25% + 75% == 100%, so each gets its full requested share and they exactly fill the parent.
        assertEquals(250, quarter.measuredWidth)
        assertEquals(750, threeQuarter.measuredWidth)
    }

    @Test
    public fun testHorizontalPercentChildCollapsesWhenFixedSiblingOverflows() {
        val layout = WeightlessLinearLayout(context)

        val fixed = View(context)
        val percent = View(context)

        layout.addView(fixed, fixedWidthLayoutParams(400))
        layout.addView(percent, percentWidthLayoutParams())

        // Parent is narrower than the fixed child alone, so siblings already overflow.
        layout.measure(
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST)
        )

        // Fixed child wins; the percent child collapses to 0px (iOS failure behavior).
        assertEquals(400, fixed.measuredWidth)
        assertEquals(0, percent.measuredWidth)
    }

    @Test
    public fun testHorizontalWrapContentChildCollapsesWhenFixedSiblingOverflows() {
        val layout = WeightlessLinearLayout(context)

        val fixed = View(context)
        val wrap = FixedSizeView(context, w = 100, h = 50)

        layout.addView(fixed, fixedWidthLayoutParams(400))
        layout.addView(wrap, wrapLayoutParams())

        layout.measure(
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST)
        )

        // The non-shrinkable wrap-content child is zeroed so the fixed child keeps its size.
        assertEquals(400, fixed.measuredWidth)
        assertEquals(0, wrap.measuredWidth)
    }

    @Test
    public fun testVerticalPercentChildCollapsesWhenFixedSiblingOverflows() {
        val layout = WeightlessLinearLayout(context)
        layout.setOrientation(WeightlessLinearLayout.OrientationMode.VERTICAL)

        val fixed = View(context)
        val percent = View(context)

        layout.addView(fixed, fixedHeightLayoutParams(400))
        layout.addView(percent, percentHeightLayoutParams())

        layout.measure(
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY)
        )

        assertEquals(400, fixed.measuredHeight)
        assertEquals(0, percent.measuredHeight)
    }

    private fun percentWidthLayoutParams(
        percent: Float = 1f,
        marginStart: Int = 0,
        marginEnd: Int = 0
    ): WeightlessLinearLayout.LayoutParams {
        return WeightlessLinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, percent, 0f
        ).apply {
            this.marginStart = marginStart
            this.marginEnd = marginEnd
        }
    }

    private fun fixedWidthLayoutParams(width: Int): WeightlessLinearLayout.LayoutParams {
        return WeightlessLinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun fixedHeightLayoutParams(height: Int): WeightlessLinearLayout.LayoutParams {
        return WeightlessLinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height)
    }

    private fun wrapLayoutParams(): WeightlessLinearLayout.LayoutParams {
        return WeightlessLinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    /** A view that reports a fixed natural size but still honors EXACTLY/AT_MOST specs. */
    private class FixedSizeView(context: Context, val w: Int, val h: Int) : View(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(
                resolveSize(w, widthMeasureSpec),
                resolveSize(h, heightMeasureSpec)
            )
        }
    }

    private fun percentHeightLayoutParams(
        topMargin: Int = 0,
        bottomMargin: Int = 0
    ): WeightlessLinearLayout.LayoutParams {
        return WeightlessLinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0f, 1f
        ).apply {
            this.topMargin = topMargin
            this.bottomMargin = bottomMargin
        }
    }
}

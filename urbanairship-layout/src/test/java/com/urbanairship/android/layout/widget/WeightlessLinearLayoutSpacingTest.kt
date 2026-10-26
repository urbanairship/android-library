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

@RunWith(RobolectricTestRunner::class)
public class WeightlessLinearLayoutSpacingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val atMost = View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST)
    private fun exactly(px: Int) = View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY)

    private fun layout(
        orientation: WeightlessLinearLayout.OrientationMode,
        spacing: Int
    ) = WeightlessLinearLayout(context).also {
        it.setOrientation(orientation)
        it.spacing = spacing
    }

    private fun fixed(w: Int, h: Int) = WeightlessLinearLayout.LayoutParams(w, h)

    private fun percentWidth(percent: Float) =
        WeightlessLinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, percent, 0f)

    private fun percentHeight(percent: Float) =
        WeightlessLinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0f, percent)

    private fun <T : View> T.addTo(layout: WeightlessLinearLayout, lp: ViewGroup.LayoutParams): T =
        also { layout.addView(it, lp) }

    private fun WeightlessLinearLayout.measureAndLayout(widthSpec: Int, heightSpec: Int) {
        measure(widthSpec, heightSpec)
        layout(0, 0, measuredWidth, measuredHeight)
    }

    @Test
    public fun verticalGapsSitBetweenAdjacentChildrenOnly() {
        val l = layout(WeightlessLinearLayout.OrientationMode.VERTICAL, 10)
        val a = View(context).addTo(l, fixed(50, 40))
        val b = View(context).addTo(l, fixed(50, 40))
        val c = View(context).addTo(l, fixed(50, 40))

        l.measureAndLayout(atMost, atMost)

        assertEquals(0, a.top)
        assertEquals(50, b.top)
        assertEquals(100, c.top)
        // Three children and two gaps, nothing leading or trailing.
        assertEquals(140, l.measuredHeight)
        assertEquals(50, l.measuredWidth)
    }

    @Test
    public fun horizontalGapsSitBetweenAdjacentChildrenOnly() {
        val l = layout(WeightlessLinearLayout.OrientationMode.HORIZONTAL, 10)
        val a = View(context).addTo(l, fixed(40, 50))
        val b = View(context).addTo(l, fixed(40, 50))
        val c = View(context).addTo(l, fixed(40, 50))

        l.measureAndLayout(atMost, atMost)

        assertEquals(0, a.left)
        assertEquals(50, b.left)
        assertEquals(100, c.left)
        assertEquals(140, l.measuredWidth)
        assertEquals(50, l.measuredHeight)
    }

    @Test
    public fun spacingAddsToChildMargins() {
        val l = layout(WeightlessLinearLayout.OrientationMode.VERTICAL, 10)
        View(context).addTo(l, fixed(50, 40).apply { bottomMargin = 3 })
        val b = View(context).addTo(l, fixed(50, 40).apply { topMargin = 5 })

        l.measureAndLayout(atMost, atMost)

        assertEquals(40 + 3 + 10 + 5, b.top)
        assertEquals(40 + 3 + 10 + 5 + 40, l.measuredHeight)
    }

    @Test
    public fun spacingLeavesTheCrossAxisAlone() {
        val l = layout(WeightlessLinearLayout.OrientationMode.VERTICAL, 10)
        val a = View(context).addTo(l, fixed(50, 40))
        val b = View(context).addTo(l, fixed(50, 40))

        l.measureAndLayout(atMost, atMost)

        assertEquals(a.left, b.left)
        assertEquals(50, l.measuredWidth)
    }

    @Test
    public fun goneChildTakesNoGap() {
        val l = layout(WeightlessLinearLayout.OrientationMode.VERTICAL, 10)
        View(context).addTo(l, fixed(50, 40))
        val hidden = View(context).addTo(l, fixed(50, 40))
        val c = View(context).addTo(l, fixed(50, 40))
        hidden.visibility = View.GONE

        l.measureAndLayout(atMost, atMost)

        assertEquals(50, c.top)
        assertEquals(90, l.measuredHeight)
    }

    @Test
    public fun goneFirstChildLeavesNoLeadingGap() {
        val l = layout(WeightlessLinearLayout.OrientationMode.VERTICAL, 10)
        val hidden = View(context).addTo(l, fixed(50, 40))
        val b = View(context).addTo(l, fixed(50, 40))
        val c = View(context).addTo(l, fixed(50, 40))
        hidden.visibility = View.GONE

        l.measureAndLayout(atMost, atMost)

        assertEquals(0, b.top)
        assertEquals(50, c.top)
    }

    @Test
    public fun gapReturnsWhenAGoneChildIsShownAgain() {
        val l = layout(WeightlessLinearLayout.OrientationMode.VERTICAL, 10)
        View(context).addTo(l, fixed(50, 40))
        val b = View(context).addTo(l, fixed(50, 40))
        b.visibility = View.GONE
        l.measureAndLayout(atMost, atMost)
        assertEquals(40, l.measuredHeight)

        b.visibility = View.VISIBLE
        l.measureAndLayout(atMost, atMost)

        assertEquals(50, b.top)
        assertEquals(90, l.measuredHeight)
    }

    @Test
    public fun repeatedMeasuresDoNotAccumulateGap() {
        val l = layout(WeightlessLinearLayout.OrientationMode.HORIZONTAL, 10)
        View(context).addTo(l, fixed(40, 50))
        val b = View(context).addTo(l, fixed(40, 50))

        repeat(3) { l.measureAndLayout(atMost, atMost) }

        assertEquals(50, b.left)
        assertEquals(90, l.measuredWidth)
    }

    @Test
    public fun negativeSpacingIsZero() {
        val l = layout(WeightlessLinearLayout.OrientationMode.VERTICAL, -8)
        View(context).addTo(l, fixed(50, 40))
        val b = View(context).addTo(l, fixed(50, 40))

        l.measureAndLayout(atMost, atMost)

        assertEquals(40, b.top)
    }

    @Test
    public fun percentChildrenPaySpacingOutOfTheirSlotsLikeMargins() {
        val l = layout(WeightlessLinearLayout.OrientationMode.HORIZONTAL, 10)
        val a = View(context).addTo(l, percentWidth(0.5f))
        val b = View(context).addTo(l, percentWidth(0.5f))

        l.measureAndLayout(exactly(400), atMost)

        // The gap is the second child's leading margin, so it comes out of that child's slot.
        assertEquals(200, a.measuredWidth)
        assertEquals(190, b.measuredWidth)
        assertEquals(210, b.left)
        assertEquals(400, b.right)
    }

    @Test
    public fun verticalPercentChildrenPaySpacingOutOfTheirSlots() {
        val l = layout(WeightlessLinearLayout.OrientationMode.VERTICAL, 10)
        val a = View(context).addTo(l, percentHeight(0.5f))
        val b = View(context).addTo(l, percentHeight(0.5f))

        l.measureAndLayout(atMost, exactly(400))

        assertEquals(200, a.measuredHeight)
        assertEquals(190, b.measuredHeight)
        assertEquals(400, b.bottom)
    }

    @Test
    public fun fixedAndPercentChildrenShareWhatSpacingLeaves() {
        val l = layout(WeightlessLinearLayout.OrientationMode.HORIZONTAL, 10)
        val fixedChild = View(context).addTo(l, fixed(100, 20))
        val pct = View(context).addTo(l, percentWidth(1f))

        l.measureAndLayout(exactly(400), atMost)

        assertEquals(100, fixedChild.measuredWidth)
        assertEquals(290, pct.measuredWidth)
        assertEquals(400, pct.right)
    }

    @Test
    public fun autoAndPercentChildrenMixed() {
        val l = layout(WeightlessLinearLayout.OrientationMode.HORIZONTAL, 10)
        val auto = IntrinsicView(context, 60, 20)
            .addTo(l, fixed(ViewGroup.LayoutParams.WRAP_CONTENT, 20))
        val pct = View(context).addTo(l, percentWidth(1f))

        l.measureAndLayout(exactly(400), atMost)

        assertEquals(60, auto.measuredWidth)
        assertEquals(330, pct.measuredWidth)
        assertEquals(400, pct.right)
    }

    @Test
    public fun percentBesideFixedInAutoHeightColumnCountsSpacingAsFixedContent() {
        val l = layout(WeightlessLinearLayout.OrientationMode.VERTICAL, 10)
        val a = View(context).addTo(l, percentHeight(0.5f))
        val b = View(context).addTo(l, fixed(50, 60))

        l.measureAndLayout(exactly(300), atMost)

        assertEquals(140, l.measuredHeight)
        assertEquals(70, a.measuredHeight)
        assertEquals(80, b.top)
    }

    private class IntrinsicView(context: Context, val w: Int, val h: Int) : View(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
        }
    }
}

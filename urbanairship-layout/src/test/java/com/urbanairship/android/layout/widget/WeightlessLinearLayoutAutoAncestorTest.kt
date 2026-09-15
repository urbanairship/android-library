/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A stack whose percentages sum to 1 or more has no self-consistent length, so it falls back to
 * taking the whole bound it was given. That's the right answer against a real ceiling — fixed
 * children are paid first and the percent ones fair-share the rest — but an `AT_MOST` handed down
 * by an auto-sized ancestor isn't a ceiling, it's that ancestor's content budget passing through.
 *
 * Claiming it starved every sibling: the fixed ones overflowed the parent, which then zeroed the
 * `WRAP_CONTENT` ones to make room. In the sample scene that rendered `overflow-in-auto` as a
 * blank page and dropped `overflow-nested`'s outer percent row to nothing.
 */
@RunWith(RobolectricTestRunner::class)
public class WeightlessLinearLayoutAutoAncestorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * `overflow-in-auto`: three 100% rows in an auto stack whose ancestors are also auto. There is
     * no fixed size anywhere to take a percentage of, so the rows size to their content and the
     * labels around them survive.
     */
    @Test
    public fun testPercentTotalAtLeastOneUnderAutoAncestorDoesNotStarveSiblings() {
        // Column is a real page: fixed height, a caption, the offending stack, a footer.
        val column = verticalStack()
        val caption = FixedSizeView(context, w = 100, h = 30)
        val footer = FixedSizeView(context, w = 100, h = 30)

        val outer = verticalStack()
        val inner = verticalStack()
        val rows = (0 until 3).map { FixedSizeView(context, w = 100, h = 40) }
        rows.forEach { inner.addView(it, percentHeight(1f)) }
        outer.addView(inner, autoHeight())

        column.addView(caption, autoHeight())
        column.addView(outer, autoHeight())
        column.addView(footer, autoHeight())

        column.measure(exactly(500), exactly(1000))

        // The captions are what vanished: the stack took the full column, the column overflowed,
        // and the overflow branch zeroed every WRAP_CONTENT child to make room.
        assertEquals(30, caption.measuredHeight)
        assertEquals(30, footer.measuredHeight)

        // Percent with no length to resolve against behaves as auto, matching iOS.
        rows.forEach { assertEquals(40, it.measuredHeight) }
        assertEquals(120, inner.measuredHeight)
    }

    /**
     * `overflow-nested`: an outer stack against a real 420 ceiling, containing a fixed row, a
     * percent row, and an auto stack that itself sums past 1. The outer stack fair-shares the
     * ceiling; the inner one has no length of its own and sizes to its content.
     */
    @Test
    public fun testRealCeilingFairSharesWhileNestedAutoStackFallsBackToContent() {
        val outer = verticalStack()
        val fixed = FixedSizeView(context, w = 100, h = 140)
        val outerPercent = FixedSizeView(context, w = 100, h = 40)

        val inner = verticalStack()
        val innerFixed = FixedSizeView(context, w = 100, h = 40)
        val innerRows = (0 until 2).map { FixedSizeView(context, w = 100, h = 20) }

        outer.addView(fixed, fixedHeight(140))
        outer.addView(outerPercent, percentHeight(1f))
        inner.addView(innerFixed, fixedHeight(40))
        innerRows.forEach { inner.addView(it, percentHeight(1f)) }
        outer.addView(inner, autoHeight())

        // A fixed 420 container is a real ceiling, so the outer stack may divide it.
        outer.measure(exactly(500), atMost(420))

        // The inner stack's bound is the outer's slack, not a ceiling: content height only.
        innerRows.forEach { assertEquals(20, it.measuredHeight) }
        assertEquals(80, inner.measuredHeight)

        // ...which leaves the outer percent row a real share instead of collapsing to nothing.
        assertEquals(140, fixed.measuredHeight)
        assertTrue(
            "outer percent row collapsed (was ${outerPercent.measuredHeight})",
            outerPercent.measuredHeight > 0
        )
        assertEquals(420, fixed.measuredHeight + outerPercent.measuredHeight + inner.measuredHeight)
    }

    /**
     * The behaviour this must not disturb: a stack directly against a real ceiling still
     * fair-shares it. 200 fixed plus two 100% rows in 480 renders 200/140/140.
     */
    @Test
    public fun testRealCeilingStillFairSharesWhenPercentTotalExceedsOne() {
        val stack = verticalStack()
        val fixed = FixedSizeView(context, w = 100, h = 200)
        val a = FixedSizeView(context, w = 100, h = 20)
        val b = FixedSizeView(context, w = 100, h = 20)

        stack.addView(fixed, fixedHeight(200))
        stack.addView(a, percentHeight(1f))
        stack.addView(b, percentHeight(1f))

        stack.measure(exactly(500), atMost(480))

        assertEquals(200, fixed.measuredHeight)
        assertEquals(140, a.measuredHeight)
        assertEquals(140, b.measuredHeight)
    }

    /** The width-axis twin: 100dp plus two 100% columns in 300 renders 100/100/100. */
    @Test
    public fun testRealCeilingStillFairSharesOnWidthAxis() {
        val stack = WeightlessLinearLayout(context)
        val fixed = FixedSizeView(context, w = 100, h = 50)
        val a = FixedSizeView(context, w = 20, h = 50)
        val b = FixedSizeView(context, w = 20, h = 50)

        stack.addView(fixed, WeightlessLinearLayout.LayoutParams(100, MATCH))
        stack.addView(a, percentWidth(1f))
        stack.addView(b, percentWidth(1f))

        stack.measure(atMost(300), exactly(120))

        assertEquals(100, fixed.measuredWidth)
        assertEquals(100, a.measuredWidth)
        assertEquals(100, b.measuredWidth)
    }

    /** ...and the width-axis twin of the starvation case. */
    @Test
    public fun testAutoAncestorOnWidthAxisFallsBackToContent() {
        val outer = WeightlessLinearLayout(context)
        val inner = WeightlessLinearLayout(context)
        val cols = (0 until 3).map { FixedSizeView(context, w = 40, h = 50) }
        cols.forEach { inner.addView(it, percentWidth(1f)) }
        outer.addView(inner, autoWidth())

        outer.measure(atMost(1000), exactly(120))

        cols.forEach { assertEquals(40, it.measuredWidth) }
        assertEquals(120, inner.measuredWidth)
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    private fun verticalStack() = WeightlessLinearLayout(context).apply {
        setOrientation(WeightlessLinearLayout.OrientationMode.VERTICAL)
    }

    private fun exactly(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    private fun atMost(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.AT_MOST)

    private fun percentHeight(percent: Float) =
        WeightlessLinearLayout.LayoutParams(MATCH, 0, 0f, percent)

    private fun percentWidth(percent: Float) =
        WeightlessLinearLayout.LayoutParams(0, MATCH, percent, 0f)

    private fun fixedHeight(height: Int) = WeightlessLinearLayout.LayoutParams(MATCH, height)

    private fun autoHeight() = WeightlessLinearLayout.LayoutParams(MATCH, WRAP)

    private fun autoWidth() = WeightlessLinearLayout.LayoutParams(WRAP, MATCH)

    /** A view with a natural size that still honors EXACTLY/AT_MOST specs. */
    private class FixedSizeView(context: Context, val w: Int, val h: Int) : View(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
        }
    }
}

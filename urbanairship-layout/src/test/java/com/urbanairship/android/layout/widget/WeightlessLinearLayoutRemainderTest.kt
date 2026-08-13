/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A child is measured in the space its siblings left, the way `LinearLayout` does it.
 *
 * The space consumed so far was withheld unless a percent child had already been seen, so in a
 * stack with no percent children it was never passed at all: every child was offered the whole
 * parent. One that wanted more than the remainder took it, overflowed the stack, and was then
 * zeroed by the overflow branch — a label after a fixed row simply vanished, where stock
 * `LinearLayout` would have clamped it to what was left. See the `auto-row-remainder` page of
 * `percent-in-auto-all-cases`.
 */
@RunWith(RobolectricTestRunner::class)
public class WeightlessLinearLayoutRemainderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** A view that wants 500px on both axes but honors whatever ceiling it is given. */
    private class Greedy(context: Context) : View(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(
                resolveSize(500, widthMeasureSpec),
                resolveSize(500, heightMeasureSpec)
            )
        }
    }

    @Test
    public fun testAutoChildIsClampedToWhatSiblingsLeft() {
        val stack = verticalStack()
        val fixed = View(context)
        val greedy = Greedy(context)

        stack.addView(fixed, WeightlessLinearLayout.LayoutParams(MATCH, 200))
        stack.addView(greedy, WeightlessLinearLayout.LayoutParams(MATCH, WRAP))

        stack.measure(exactly(500), exactly(300))

        // 100px is left after the fixed row, so that's what the auto row gets — not the full 300,
        // which would overflow and get it zeroed.
        assertEquals(200, fixed.measuredHeight)
        assertEquals(100, greedy.measuredHeight)
    }

    /** The same shape through a stock LinearLayout, to pin the rule we're matching. */
    @Test
    public fun testMatchesStockLinearLayout() {
        val stock = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val fixed = View(context)
        val greedy = Greedy(context)
        stock.addView(fixed, LinearLayout.LayoutParams(MATCH, 200))
        stock.addView(greedy, LinearLayout.LayoutParams(MATCH, WRAP))
        stock.measure(exactly(500), exactly(300))

        val stack = verticalStack()
        val ourFixed = View(context)
        val ourGreedy = Greedy(context)
        stack.addView(ourFixed, WeightlessLinearLayout.LayoutParams(MATCH, 200))
        stack.addView(ourGreedy, WeightlessLinearLayout.LayoutParams(MATCH, WRAP))
        stack.measure(exactly(500), exactly(300))

        assertEquals(greedy.measuredHeight, ourGreedy.measuredHeight)
        assertEquals(fixed.measuredHeight, ourFixed.measuredHeight)
    }

    @Test
    public fun testAutoChildIsClampedToWhatSiblingsLeftOnWidthAxis() {
        val stack = WeightlessLinearLayout(context)
        val fixed = View(context)
        val greedy = Greedy(context)

        stack.addView(fixed, WeightlessLinearLayout.LayoutParams(200, MATCH))
        stack.addView(greedy, WeightlessLinearLayout.LayoutParams(WRAP, MATCH))

        stack.measure(exactly(300), exactly(500))

        assertEquals(200, fixed.measuredWidth)
        assertEquals(100, greedy.measuredWidth)
    }

    /** Three auto rows, each greedy: the budget shrinks as it is spent. */
    @Test
    public fun testRemainderShrinksAcrossSuccessiveAutoChildren() {
        val stack = verticalStack()
        val fixed = View(context)
        val a = Greedy(context)
        val b = Greedy(context)

        stack.addView(fixed, WeightlessLinearLayout.LayoutParams(MATCH, 120))
        stack.addView(a, WeightlessLinearLayout.LayoutParams(MATCH, WRAP))
        stack.addView(b, WeightlessLinearLayout.LayoutParams(MATCH, WRAP))

        stack.measure(exactly(500), exactly(300))

        // The first greedy row takes the 180 left over, leaving nothing for the second.
        assertEquals(120, fixed.measuredHeight)
        assertEquals(180, a.measuredHeight)
        assertEquals(0, b.measuredHeight)
        assertEquals(300, stack.measuredHeight)
    }

    /**
     * Percent children still measure against a settled length: the budget is withheld while they
     * are in play, since their slots only exist after the distribution pass.
     */
    @Test
    public fun testPercentChildrenStillFairShare() {
        val stack = verticalStack()
        val fixed = View(context)
        val a = View(context)
        val b = View(context)

        stack.addView(fixed, WeightlessLinearLayout.LayoutParams(MATCH, 200))
        stack.addView(a, WeightlessLinearLayout.LayoutParams(MATCH, 0, 0f, 1f))
        stack.addView(b, WeightlessLinearLayout.LayoutParams(MATCH, 0, 0f, 1f))

        stack.measure(exactly(500), atMost(480))

        assertEquals(200, fixed.measuredHeight)
        assertEquals(140, a.measuredHeight)
        assertEquals(140, b.measuredHeight)
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
}

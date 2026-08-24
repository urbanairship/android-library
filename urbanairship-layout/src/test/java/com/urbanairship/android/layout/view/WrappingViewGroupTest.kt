/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Wrapping at a maximum items per line, which is the count a score states rather than the count
 * that happens to fit.
 *
 * Both passes of the layout have to break lines the same way. Measuring made room for the lines
 * the count asks for while placing broke only on width, so a range that fitted across the parent
 * was drawn as one line under two lines' worth of height.
 */
@RunWith(RobolectricTestRunner::class)
public class WrappingViewGroupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Wider than six items, so nothing has to wrap on width alone. */
    private val parentWidth = 600
    private val itemSide = 40
    private val itemSpacing = 8
    private val lineSpacing = 8

    @Test
    public fun testBreaksLinesAtTheStatedCount() {
        val group = wrapping(items = 6, maxItemsPerLine = 3)

        val lines = group.lines()

        assertEquals(2, lines.size)
        assertEquals(listOf(3, 3), lines.map { it.size })
    }

    @Test
    public fun testPlacesTheLinesItMeasuredRoomFor() {
        val group = wrapping(items = 6, maxItemsPerLine = 3)

        val bottom = group.children().maxOf { it.bottom }

        assertEquals(itemSide * 2 + lineSpacing, group.measuredHeight)
        assertEquals(group.measuredHeight, bottom)
    }

    @Test
    public fun testSpacesTheLinesOnce() {
        val group = wrapping(items = 6, maxItemsPerLine = 3)

        val lines = group.lines()

        assertEquals(itemSide + lineSpacing, lines[1].first().top - lines[0].first().top)
    }

    @Test
    public fun testCentresEachLine() {
        val group = wrapping(items = 6, maxItemsPerLine = 3)

        group.lines().forEach { line ->
            val start = line.first().left
            val end = line.last().right
            assertEquals(parentWidth - end, start)
        }
    }

    @Test
    public fun testStillWrapsOnWidthBeforeTheCount() {
        // Ten items 40 wide with 8 between them need 472, so six is all that fits on 300.
        val group = wrapping(items = 10, maxItemsPerLine = 11, width = 300)

        assertEquals(listOf(6, 4), group.lines().map { it.size })
    }

    private fun wrapping(
        items: Int,
        maxItemsPerLine: Int,
        width: Int = parentWidth
    ): WrappingViewGroup {
        val group = WrappingViewGroup(context).apply {
            this.itemSpacing = this@WrappingViewGroupTest.itemSpacing
            this.lineSpacing = this@WrappingViewGroupTest.lineSpacing
            this.maxItemsPerLine = maxItemsPerLine
        }
        repeat(items) {
            group.addView(FixedSizeView(context, itemSide))
        }
        group.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        group.layout(0, 0, group.measuredWidth, group.measuredHeight)
        return group
    }

    private fun WrappingViewGroup.children(): List<View> =
        (0 until childCount).map { getChildAt(it) }

    /** The placed children, grouped by the row they were placed in. */
    private fun WrappingViewGroup.lines(): List<List<View>> =
        children().groupBy { it.top }.toSortedMap().values.toList()

    private class FixedSizeView(context: Context, private val side: Int) : View(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(side, side)
        }
    }
}

/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Root cause of the "gray shape floating outside the modal" bug report: a real device screenshot
 * of `_backgrounds-parallax-pager-scroll.yml`, on page 2 of 2, with no drag involved -- the gray
 * placeholder is the *pager's own* parallax background (`test-layout.internal/.../gray/1`,
 * `factor: 0.3`), not the scroll_layout's blue/teal one.
 *
 * A pager has discrete pages, unlike a ScrollLayout's one continuous scrollable region.
 * `computeHorizontalScrollOffset()` alone is a full page-width even at complete rest on page 2 --
 * settled, no drag -- which applied a *permanent* `factor * pageWidth` translate to the pager's
 * background the instant you were on any page but the first. These pin down that specific
 * mechanism against a real, laid-out `RecyclerView` + `PagerSnapHelper` (not a mocked one), rather
 * than only asserting the arithmetic in isolation.
 */
@RunWith(RobolectricTestRunner::class)
public class PagerContentOffsetTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    public fun offsetIsZeroAtRestOnTheSecondOfTwoPages() {
        val recyclerView = buildPagerRecyclerView(pageCount = 2, pageWidthPx = 1080)

        // Settle on page 2 with no drag -- exactly the reported scenario.
        recyclerView.scrollToPosition(1)
        layoutOnce(recyclerView)

        assertEquals(
            "settled on page 2 (no drag), the absolute recycler offset is a full page-width -- " +
                "confirms the bug's premise before checking the fix",
            1080,
            recyclerView.computeHorizontalScrollOffset()
        )

        val relative = relativeToNearestPage(recyclerView)
        assertEquals(
            "offset relative to the nearest page must be 0 at rest, on ANY page -- the bug was " +
                "using the absolute figure directly, which is a full page-width here and would " +
                "apply a permanent factor*1080px translate to the pager's own background",
            0f,
            relative,
            0.01f
        )
    }

    @Test
    public fun offsetIsZeroAtRestOnTheFirstPage() {
        val recyclerView = buildPagerRecyclerView(pageCount = 2, pageWidthPx = 1080)
        layoutOnce(recyclerView)

        assertEquals(0f, relativeToNearestPage(recyclerView), 0.01f)
    }

    @Test
    public fun offsetRangesSmoothlyMidDrag() {
        val recyclerView = buildPagerRecyclerView(pageCount = 2, pageWidthPx = 1080)
        layoutOnce(recyclerView)

        // Dragged 400px from page 1 toward page 2 -- short of the halfway point, so "nearest
        // page" is unambiguously still page 1 and the offset is unambiguously positive.
        recyclerView.scrollBy(400, 0)

        assertEquals(
            "mid-drag, partway to the next page, should read as that many pixels of offset",
            400f,
            relativeToNearestPage(recyclerView),
            1f
        )
    }

    /** The exact formula in `PagerRecyclerView.onScrolled`, applied to a real RecyclerView. */
    private fun relativeToNearestPage(recyclerView: RecyclerView): Float {
        val pageWidth = recyclerView.width.toFloat()
        val absolute = recyclerView.computeHorizontalScrollOffset().toFloat()
        val nearestPage = Math.round(absolute / pageWidth)
        return absolute - nearestPage * pageWidth
    }

    private fun buildPagerRecyclerView(pageCount: Int, pageWidthPx: Int): RecyclerView {
        val recyclerView = RecyclerView(context)
        val layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = FixedWidthAdapter(pageCount, pageWidthPx)
        PagerSnapHelper().attachToRecyclerView(recyclerView)

        recyclerView.measure(
            View.MeasureSpec.makeMeasureSpec(pageWidthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
        )
        recyclerView.layout(0, 0, pageWidthPx, 600)
        return recyclerView
    }

    /** Forces a second layout pass, needed after `scrollToPosition` actually repositions. */
    private fun layoutOnce(recyclerView: RecyclerView) {
        recyclerView.measure(
            View.MeasureSpec.makeMeasureSpec(recyclerView.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(recyclerView.height, View.MeasureSpec.EXACTLY)
        )
        recyclerView.layout(0, 0, recyclerView.width, recyclerView.height)
    }

    private class FixedWidthAdapter(
        private val count: Int,
        private val pageWidthPx: Int
    ) : RecyclerView.Adapter<FixedWidthAdapter.Holder>() {

        class Holder(view: View) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = View(parent.context)
            view.layoutParams = RecyclerView.LayoutParams(pageWidthPx, ViewGroup.LayoutParams.MATCH_PARENT)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) = Unit

        override fun getItemCount(): Int = count
    }
}

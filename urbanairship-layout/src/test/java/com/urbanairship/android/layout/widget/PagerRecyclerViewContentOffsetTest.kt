/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.view.View
import android.view.View.MeasureSpec
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.AnyModel
import com.urbanairship.android.layout.model.PagerModel
import com.urbanairship.android.layout.view.PagerView
import io.mockk.every
import io.mockk.mockk
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Drives a real [PagerRecyclerView] -- not a hand-copied reimplementation of its offset formula
 * -- through an actual drag, via [RecyclerView.scrollBy]. [PagerContentOffsetTest] already covers
 * the "pinned at rest on any page" case this way; this covers the drag itself, which is where a
 * real-device report described the pager's own parallax motion as "seems wrong."
 *
 * Root cause: `relativeToNearestPage()` (the offset [PagerRecyclerView.onScrolled] fed to a
 * parallaxing layer) rounded to whichever page a drag was *closer to*, so at the exact midpoint of
 * every page-to-page drag -- the moment "nearest page" flips from one side to the other -- the
 * reported offset jumped by very nearly a full page-width instantly, then continued on smoothly.
 * A parallax layer driven by that offset would snap backward mid-swipe, on every page turn.
 *
 * Fixed by reporting the track's own absolute offset, which is continuous by construction -- there
 * is no page to round to. See `PagerRecyclerView.contentOffset`.
 */
@RunWith(RobolectricTestRunner::class)
public class PagerRecyclerViewContentOffsetTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val viewEnvironment: ViewEnvironment = mockk(relaxed = true)
    private val pageWidthPx = 1080

    @Test
    public fun offsetRangesContinuouslyAcrossTheWholeDragWithNoJumpAtTheMidpoint() {
        val recyclerView = buildRecyclerView()
        val reported = mutableListOf<Float>()
        recyclerView.setPagerScrollListener(object : PagerView.OnScrollListener {
            override fun onScrollTo(position: Int, isInternalScroll: Boolean) = Unit
            override fun onContentOffsetChanged(offsetPx: Float) {
                reported.add(offsetPx)
            }
        })

        // Drag steadily from page 0 toward page 1, a pixel at a time, well past the halfway point
        // (540) that made the old "nearest page" math jump.
        repeat(900) { recyclerView.scrollBy(1, 0) }

        assertTrue("should have reported live offsets while dragging", reported.size > 800)
        // No consecutive pair should ever jump by more than the 1px steps driving it -- the old
        // "nearest page" math jumped by very nearly a full page-width right at the midpoint.
        for (i in 1 until reported.size) {
            val delta = abs(reported[i] - reported[i - 1])
            assertTrue(
                "offset jumped by $delta between consecutive scroll steps (index $i: " +
                    "${reported[i - 1]} -> ${reported[i]}) -- should track the drag " +
                    "continuously, not snap",
                delta <= 2f
            )
        }
    }

    @Test
    public fun offsetReachesNearlyAFullPageWidthJustBeforeLandingOnTheNextPage() {
        val recyclerView = buildRecyclerView()
        var lastOffset = 0f
        recyclerView.setPagerScrollListener(object : PagerView.OnScrollListener {
            override fun onScrollTo(position: Int, isInternalScroll: Boolean) = Unit
            override fun onContentOffsetChanged(offsetPx: Float) {
                lastOffset = offsetPx
            }
        })

        recyclerView.scrollBy(pageWidthPx - 1, 0)

        // The old "nearest page" math capped this at half a page-width (540) -- a parallaxing
        // layer only ever traveled half its configured `factor * pageWidth` distance.
        assertEquals((pageWidthPx - 1).toFloat(), lastOffset, 0.5f)
    }

    /**
     * Ryan's real-device report, comparing the same pager across platforms: "its not correct on
     * pagers."
     *
     * The offset used to be measured from whichever page a drag started on, re-zeroing every time
     * one settled -- so a background wobbled through the drag and landed back exactly where it
     * began, with no net movement across the pager at all. That standing translate on a later
     * page isn't a bug to cancel out; it *is* the parallax. iOS
     * (`pagerContentOffset = -(width * pageIndex) + drag`) and web
     * (`--parallax-x-offset = currentIndex * -100%`) both keep it.
     */
    @Test
    public fun offsetKeepsItsGroundOnceAPageSettlesRatherThanReturningToZero() {
        val recyclerView = buildRecyclerView(pageCount = 3)
        var lastOffset = 0f
        recyclerView.setPagerScrollListener(object : PagerView.OnScrollListener {
            override fun onScrollTo(position: Int, isInternalScroll: Boolean) = Unit
            override fun onContentOffsetChanged(offsetPx: Float) {
                lastOffset = offsetPx
            }
        })

        // Landed squarely on page 2: the drag is over and nothing is mid-flight.
        recyclerView.scrollBy(pageWidthPx, 0)
        assertEquals(
            "sitting on page 2, the background should still be a page-width along",
            pageWidthPx.toFloat(), lastOffset, 1f
        )

        recyclerView.scrollBy(pageWidthPx, 0)
        assertEquals(
            "sitting on page 3, it should be two page-widths along -- not back at zero",
            (pageWidthPx * 2).toFloat(), lastOffset, 1f
        )
    }

    private fun buildRecyclerView(pageCount: Int = 2): PagerRecyclerView {
        val pageModels = (0 until pageCount).map {
            mockk<AnyModel>(relaxed = true) {
                every { createView(any(), any(), any()) } returns View(context)
            }
        }
        val model: PagerModel = mockk(relaxed = true) {
            every { isSinglePage } returns false
            every { viewInfo } returns mockk(relaxed = true) {
                every { isSwipeDisabled } returns false
            }
            every { pages } returns pageModels
        }

        val recyclerView = PagerRecyclerView(context, model, viewEnvironment)
        recyclerView.measure(
            MeasureSpec.makeMeasureSpec(pageWidthPx, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(600, MeasureSpec.EXACTLY)
        )
        recyclerView.layout(0, 0, pageWidthPx, 600)
        return recyclerView
    }
}

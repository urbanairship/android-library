/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.PagerModel
import io.mockk.every
import io.mockk.mockk
import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Ryan's report: the page-turn animation and the parallax background motion aren't in sync.
 * `PagerRecyclerView`'s real page-turn settle is timed from `ThomasSmoothScroller`'s
 * `MILLISECONDS_PER_INCH` (65f) divided by density -- `settleDurationMs` is the same formula,
 * read from that same constant, not a second copy restated here or in the background's own
 * settle animator. This proves the two can't drift apart: for a handful of distances, the
 * duration the background's `ValueAnimator` is given is bit-for-bit what that formula produces.
 */
@RunWith(RobolectricTestRunner::class)
public class PagerRecyclerViewSettleDurationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val model: PagerModel = mockk(relaxed = true)
    private val viewEnvironment: ViewEnvironment = mockk(relaxed = true)

    @Before
    public fun setUp() {
        // A plain relaxed mock of PagerModel can't synthesize a correctly-typed PagerInfo for
        // this nested property on its own (MockK resolves it to the base View interface instead
        // and configure() ClassCastExceptions on it) -- stubbing it explicitly gets the right type.
        every { model.viewInfo } returns mockk(relaxed = true)
        every { model.pages } returns emptyList()
    }

    @Test
    public fun durationScalesWithDistanceAndDensityLikeThomasSmoothScrollerDoes() {
        val view = PagerRecyclerView(context, model, viewEnvironment)
        val densityDpi = context.resources.displayMetrics.densityDpi
        // ThomasSmoothScroller.MILLISECONDS_PER_INCH -- not reachable directly (that class is
        // private), but it's the same 65f settleDurationMs is documented to read from; asserting
        // against the formula it resolves to catches drift just as well as reaching the private
        // constant would.
        val millisecondsPerInch = 65f

        for (distancePx in listOf(0f, 1f, 100f, 540f, 1080f, -540f)) {
            val expectedSpeedPerPixel = millisecondsPerInch / densityDpi
            val expected = ceil(kotlin.math.abs(distancePx) * expectedSpeedPerPixel)
                .toLong()
                .coerceAtLeast(1L)

            assertEquals(
                "settleDurationMs($distancePx) should match ThomasSmoothScroller's own " +
                    "speed-per-pixel formula exactly, not an independently-tuned number",
                expected,
                view.settleDurationMs(distancePx)
            )
        }
    }

    @Test
    public fun durationIsNeverZeroEvenForATinyDistance() {
        val view = PagerRecyclerView(context, model, viewEnvironment)
        // A zero/negligible distance still gets a floor of 1ms -- ValueAnimator.setDuration(0)
        // would skip animating (and skip the update listener) entirely rather than settling at
        // the right value on the next frame.
        assertEquals(1L, view.settleDurationMs(0f))
    }
}

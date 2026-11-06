/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.graphics.Rect
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Where the focus ring's outer edge lands relative to the area it may draw in. */
@RunWith(RobolectricTestRunner::class)
public class FitRingTest {

    private val clip = Rect(0, 0, 1000, 600)
    private val reach = 16f

    @Test
    public fun ringWithRoomOnEverySideSitsOutsideTheView() {
        assertEquals(RectF(84f, 84f, 316f, 216f), fit(Rect(100, 100, 300, 200)))
    }

    @Test
    public fun viewFillingTheClipGetsItsRingInside() {
        assertEquals(RectF(0f, 0f, 1000f, 600f), fit(Rect(0, 0, 1000, 600)))
    }

    @Test
    public fun onlyTheSidesWithoutRoomMoveIn() {
        assertEquals(RectF(884f, 0f, 1000f, 116f), fit(Rect(900, 0, 1000, 100)))
    }

    @Test
    public fun sideWithPartialRoomMovesInOnlyAsFarAsItMust() {
        assertEquals(RectF(84f, 84f, 1000f, 216f), fit(Rect(100, 100, 990, 200)))
    }

    @Test
    public fun viewExtendingPastTheClipGetsAClosedRingAlongTheClip() {
        assertEquals(RectF(84f, 484f, 316f, 600f), fit(Rect(100, 500, 300, 800)))
    }

    @Test
    public fun clipOffsetFromTheOriginIsRespected() {
        val modalFrame = Rect(200, 100, 800, 500)
        val out = RectF()
        fitRing(Rect(200, 300, 800, 500), modalFrame, reach, out)
        assertEquals(RectF(200f, 284f, 800f, 500f), out)
    }

    private fun fit(target: Rect): RectF = RectF().also { fitRing(target, clip, reach, it) }
}

/* Copyright Airship and Contributors */

package com.urbanairship.automation.compose

import androidx.compose.foundation.gestures.Orientation
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.property.BannerPlacement
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class BannerGestureTest {

    //
    // Swipe axis
    //

    @Test
    public fun testSwipeAxis() {
        // Top and bottom placements are swiped vertically.
        assertEquals(Orientation.Vertical, placement("center", "top").swipeAxis())
        assertEquals(Orientation.Vertical, placement("center", "bottom").swipeAxis())
        assertEquals(Orientation.Vertical, placement("end", "top").swipeAxis())
        assertEquals(Orientation.Vertical, placement("start", "bottom").swipeAxis())

        // Vertically centered placements are swiped horizontally.
        assertEquals(Orientation.Horizontal, placement("start", "center").swipeAxis())
        assertEquals(Orientation.Horizontal, placement("end", "center").swipeAxis())
    }

    //
    // Dismiss direction
    //

    @Test
    public fun testDismissDirectionVerticalEdges() {
        // Vertical placements ignore the layout direction.
        assertEquals(-1f, placement("center", "top").dismissDirection(isRtl = false))
        assertEquals(-1f, placement("center", "top").dismissDirection(isRtl = true))
        assertEquals(1f, placement("center", "bottom").dismissDirection(isRtl = false))
        assertEquals(1f, placement("center", "bottom").dismissDirection(isRtl = true))

        // Corner placements still dismiss along the vertical axis, regardless of layout direction.
        assertEquals(-1f, placement("end", "top").dismissDirection(isRtl = false))
        assertEquals(-1f, placement("end", "top").dismissDirection(isRtl = true))
        assertEquals(1f, placement("start", "bottom").dismissDirection(isRtl = false))
        assertEquals(1f, placement("start", "bottom").dismissDirection(isRtl = true))
    }

    @Test
    public fun testDismissDirectionHorizontalEdges() {
        // Center-start dismisses toward the start edge: left in LTR, right in RTL.
        assertEquals(-1f, placement("start", "center").dismissDirection(isRtl = false))
        assertEquals(1f, placement("start", "center").dismissDirection(isRtl = true))

        // Center-end dismisses toward the end edge: right in LTR, left in RTL.
        assertEquals(1f, placement("end", "center").dismissDirection(isRtl = false))
        assertEquals(-1f, placement("end", "center").dismissDirection(isRtl = true))
    }

    //
    // Should-dismiss predicate
    //

    @Test
    public fun testIdleReleaseThreshold() {
        // An idle release at 39% of the frame extent stays; at 40% it dismisses.
        assertFalse(shouldDismissBanner(39f, 0f, 100f, 1f, MIN_FLING))
        assertTrue(shouldDismissBanner(40f, 0f, 100f, 1f, MIN_FLING))

        // Mirrored for a negative dismiss direction.
        assertFalse(shouldDismissBanner(-39f, 0f, 100f, -1f, MIN_FLING))
        assertTrue(shouldDismissBanner(-40f, 0f, 100f, -1f, MIN_FLING))
    }

    @Test
    public fun testFlingTowardDismiss() {
        // A fling toward the dismiss edge dismisses past 10% drag...
        assertTrue(shouldDismissBanner(11f, MIN_FLING, 100f, 1f, MIN_FLING))
        // ...but exactly 10% is not enough.
        assertFalse(shouldDismissBanner(10f, MIN_FLING, 100f, 1f, MIN_FLING))
        // Below the minimum fling velocity, the idle threshold applies instead.
        assertFalse(shouldDismissBanner(11f, MIN_FLING - 1f, 100f, 1f, MIN_FLING))

        // Mirrored for a negative dismiss direction.
        assertTrue(shouldDismissBanner(-11f, -MIN_FLING, 100f, -1f, MIN_FLING))
        assertFalse(shouldDismissBanner(-10f, -MIN_FLING, 100f, -1f, MIN_FLING))
    }

    @Test
    public fun testMovedAwayNeverDismisses() {
        // A release away from the dismiss edge never dismisses, regardless of velocity.
        assertFalse(shouldDismissBanner(-50f, -10_000f, 100f, 1f, MIN_FLING))
        assertFalse(shouldDismissBanner(-50f, 10_000f, 100f, 1f, MIN_FLING))

        // A fling back toward resting while dragged toward the dismiss edge is judged by the
        // idle drag threshold alone.
        assertFalse(shouldDismissBanner(30f, -10_000f, 100f, 1f, MIN_FLING))
        assertTrue(shouldDismissBanner(40f, -10_000f, 100f, 1f, MIN_FLING))
    }

    @Test
    public fun testUnmeasuredFrameNeverDismisses() {
        // With an unmeasured (zero) frame extent, the drag percent is treated as zero, so a
        // release never dismisses, even with a large drag or fling.
        assertFalse(shouldDismissBanner(500f, 0f, 0f, 1f, MIN_FLING))
        assertFalse(shouldDismissBanner(500f, 10_000f, 0f, 1f, MIN_FLING))
    }

    private fun placement(horizontal: String, vertical: String): BannerPlacement =
        BannerPlacement.fromJson(JsonValue.parseString(
            """
            {
              "size": { "width": "100%", "height": "auto" },
              "position": { "horizontal": "$horizontal", "vertical": "$vertical" }
            }
            """.trimIndent()
        ))

    private companion object {
        const val MIN_FLING = 1000f
    }
}

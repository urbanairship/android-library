package com.urbanairship.images

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests for [targetDimension]. */
public class TargetDimensionTest {

    @Test
    public fun testMeasuredOnly() {
        assertEquals(454, targetDimension(measured = 454, resolved = null, fallback = 1080))
    }

    @Test
    public fun testResolvedOnly() {
        assertEquals(972, targetDimension(measured = null, resolved = 972, fallback = 1080))
    }

    @Test
    public fun testResolvedWinsOverASmallerMeasurement() {
        assertEquals(972, targetDimension(measured = 87, resolved = 972, fallback = 1080))
    }

    /** A view that is already larger than the bound keeps its own size. */
    @Test
    public fun testMeasuredWinsWhenLarger() {
        assertEquals(1200, targetDimension(measured = 1200, resolved = 972, fallback = 1080))
    }

    @Test
    public fun testFallsBackWhenNeitherIsKnown() {
        assertEquals(1080, targetDimension(measured = null, resolved = null, fallback = 1080))
    }

    @Test
    public fun testZerosFallBack() {
        assertEquals(1080, targetDimension(measured = 0, resolved = 0, fallback = 1080))
    }
}

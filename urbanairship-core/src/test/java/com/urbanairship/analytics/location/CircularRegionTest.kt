/* Copyright Airship and Contributors */
package com.urbanairship.analytics.location

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class CircularRegionTest {

    /**
     * Test isValid returns true for valid circular region.
     */
    @Test
    public fun testValidCircularRegion() {
        val circularRegion = CircularRegion(10.0, 0.0, 0.0)
        assertTrue(circularRegion.isValid)
    }

    /**
     * Test setting radius above the max allowed is invalid.
     */
    @Test
    public fun testRadiusAboveMax() {
        val circularRegion = CircularRegion(100001.0, 0.0, 0.0)
        assertFalse(circularRegion.isValid)
    }

    /**
     * Test setting radius below the min allowed is invalid.
     */
    @Test
    public fun testRadiusBelowMin() {
        val circularRegion = CircularRegion(-1.0, 0.0, 0.0)
        assertFalse(circularRegion.isValid)
    }

    /**
     * Test setting a latitude above the max allowed is invalid.
     */
    @Test
    public fun testLatitudeAboveMax() {
        val circularRegion = CircularRegion(10.0, 91.0, 0.0)
        assertFalse(circularRegion.isValid)
    }

    /**
     * Test setting latitude below the min allowed is invalid.
     */
    @Test
    public fun testLatitudeBelowMin() {
        val circularRegion = CircularRegion(10.0, -91.0, 0.0)
        assertFalse(circularRegion.isValid)
    }

    /**
     * Test setting a longitude above the max allowed is invalid.
     */
    @Test
    public fun testLongitudeAboveMax() {
        val circularRegion = CircularRegion(10.0, 0.0, 181.0)
        assertFalse(circularRegion.isValid)
    }

    /**
     * Test setting longitude below the min allowed is invalid.
     */
    @Test
    public fun testLongitudeBelowMin() {
        val circularRegion = CircularRegion(10.0, 0.0, -181.0)
        assertFalse(circularRegion.isValid)
    }
}

/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.location;

import com.urbanairship.BaseTestCase;

import org.junit.Test;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class CircularRegionTest extends BaseTestCase {

    /**
     * Test isValid returns true for valid circular region.
     */
    @Test
    public void testValidCircularRegion() {
        CircularRegion circularRegion = new CircularRegion(10, 0.0, 0.0);
        assertTrue(circularRegion.isValid());
    }

    /**
     * Test setting radius above the max allowed is invalid.
     */
    @Test
    public void testRadiusAboveMax() {
        CircularRegion circularRegion = new CircularRegion(100001, 0.0, 0.0);
        assertFalse(circularRegion.isValid());
    }

    /**
     * Test setting radius below the min allowed is invalid.
     */
    @Test
    public void testRadiusBelowMin() {
        CircularRegion circularRegion = new CircularRegion(-1, 0.0, 0.0);
        assertFalse(circularRegion.isValid());
    }

    /**
     * Test setting a latitude above the max allowed is invalid.
     */
    @Test
    public void testLatitudeAboveMax() {
        CircularRegion circularRegion = new CircularRegion(10, 91.0, 0.0);
        assertFalse(circularRegion.isValid());
    }

    /**
     * Test setting latitude below the min allowed is invalid.
     */
    @Test
    public void testLatitudeBelowMin() {
        CircularRegion circularRegion = new CircularRegion(10, -91.0, 0.0);
        assertFalse(circularRegion.isValid());
    }

    /**
     * Test setting a longitude above the max allowed is invalid.
     */
    @Test
    public void testLongitudeAboveMax() {
        CircularRegion circularRegion = new CircularRegion(10, 0.0, 181.0);
        assertFalse(circularRegion.isValid());
    }

    /**
     * Test setting longitude below the min allowed is invalid.
     */
    @Test
    public void testLongitudeBelowMin() {
        CircularRegion circularRegion = new CircularRegion(10, 0.0, -181.0);
        assertFalse(circularRegion.isValid());
    }
}

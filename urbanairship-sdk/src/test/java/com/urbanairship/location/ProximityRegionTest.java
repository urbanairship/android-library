/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.location;

import com.urbanairship.BaseTestCase;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

public class ProximityRegionTest extends BaseTestCase {

    /**
     * Test isValid returns true for valid proximity region.
     */
    @Test
    public void testValidProximityRegion() {
        ProximityRegion proximityRegion = new ProximityRegion("valid_proximity_region", 1, 2);
        assertTrue(proximityRegion.isValid());
    }

    /**
     * Test setting a null proximity ID is invalid.
     */
    @Test
    public void testNullProximityId() {
        ProximityRegion proximityRegion = new ProximityRegion(null, 1, 2);
        assertFalse(proximityRegion.isValid());
    }

    /**
     * Test setting a proximity ID above the max allowed character length is invalid.
     */
    @Test
    public void testProximityIdAboveMax() {
        String proximityId = createFixedSizeString('a', 256);
        ProximityRegion proximityRegion = new ProximityRegion(proximityId, 1, 2);
        assertFalse(proximityRegion.isValid());
    }

    /**
     * Test setting a proximity ID below the min allowed character length is invalid.
     */
    @Test
    public void testProximityIdBelowMin() {
        ProximityRegion proximityRegion = new ProximityRegion("", 1, 2);
        assertFalse(proximityRegion.isValid());
    }

    /**
     * Test setting a major above the max allowed is invalid.
     */
    @Test
    public void testMajorAboveMax() {
        ProximityRegion proximityRegion = new ProximityRegion("proximity region test", 65536, 2);
        assertFalse(proximityRegion.isValid());
    }

    /**
     * Test setting a major below the min allowed is invalid.
     */
    @Test
    public void testMajorBelowMin() {
        ProximityRegion proximityRegion = new ProximityRegion("proximity region test", -1, 2);
        assertFalse(proximityRegion.isValid());
    }

    /**
     * Test setting a minor above the max allowed is invalid.
     */
    @Test
    public void testMinorAboveMax() {
        ProximityRegion proximityRegion = new ProximityRegion("proximity region test", 1, 65536);
        assertFalse(proximityRegion.isValid());
    }

    /**
     * Test setting a minor below the min allowed is invalid.
     */
    @Test
    public void testMinorBelowMin() {
        ProximityRegion proximityRegion = new ProximityRegion("proximity region test", 1, -1);
        assertFalse(proximityRegion.isValid());
    }

    /**
     * Test setting a latitude above the max allowed is invalid.
     */
    @Test
    public void testLatitudeAboveMax() {
        ProximityRegion proximityRegion = new ProximityRegion("test proximity region", 1, 2);
        proximityRegion.setCoordinates(91.0, 180.0);

        // Proximity region should still be valid even if optional properties are improperly set
        assertTrue(proximityRegion.isValid());

        assertNull(proximityRegion.getLatitude());
        assertNull(proximityRegion.getLongitude());
    }

    /**
     * Test setting a latitude below the min allowed is invalid.
     */
    @Test
    public void testLatitudeBelowMin() {
        ProximityRegion proximityRegion = new ProximityRegion("test proximity region", 1, 2);
        proximityRegion.setCoordinates(-91.0, -180.0);

        // Proximity region should still be valid even if optional properties are improperly set
        assertTrue(proximityRegion.isValid());

        assertNull(proximityRegion.getLatitude());
        assertNull(proximityRegion.getLongitude());
    }

    /**
     * Test setting longitude to a valid value then setting it back to null.
     */
    @Test
    public void testLongitudeNullable() {
        ProximityRegion proximityRegion = new ProximityRegion("test proximity region", 1, 2);

        proximityRegion.setCoordinates(0.0, 0.0);
        assertEquals(0.0, proximityRegion.getLatitude());
        assertEquals(0.0, proximityRegion.getLongitude());

        proximityRegion.setCoordinates(null, null);
        assertNull(proximityRegion.getLatitude());
        assertNull(proximityRegion.getLongitude());
    }

    /**
     * Test setting a longitude above the max allowed is invalid.
     */
    @Test
    public void testLongitudeAboveMax() {
        ProximityRegion proximityRegion = new ProximityRegion("test proximity region", 1, 2);
        proximityRegion.setCoordinates(90.0, 181.0);

        // Proximity region should still be valid even if optional properties are improperly set
        assertTrue(proximityRegion.isValid());

        assertNull(proximityRegion.getLatitude());
        assertNull(proximityRegion.getLongitude());
    }

    /**
     * Test setting a longitude below the min allowed is invalid.
     */
    @Test
    public void testLongitudeBelowMin() {
        ProximityRegion proximityRegion = new ProximityRegion("test proximity region", 1, 2);
        proximityRegion.setCoordinates(-90.0, -181.0);

        // Proximity region should still be valid even if optional properties are improperly set
        assertTrue(proximityRegion.isValid());

        assertNull(proximityRegion.getLatitude());
        assertNull(proximityRegion.getLongitude());
    }

    /**
     * Test setting rssi to a valid value then setting it back to null.
     */
    @Test
    public void testRssiNullable() {
        ProximityRegion proximityRegion = new ProximityRegion("test proximity region", 1, 2);

        proximityRegion.setRssi(-59);
        assertEquals(-59, proximityRegion.getRssi().intValue());

        proximityRegion.setRssi(null);
        assertNull(proximityRegion.getRssi());
    }

    /**
     * Test setting an rssi above the max allowed is invalid.
     */
    @Test
    public void testRssiAboveMax() {
        ProximityRegion proximityRegion = new ProximityRegion("test proximity region", 1, 2);
        proximityRegion.setRssi(101);

        // Proximity region should still be valid even if optional properties are improperly set
        assertTrue(proximityRegion.isValid());

        assertNull(proximityRegion.getRssi());
    }

    /**
     * Test setting an rssi below the min allowed is invalid.
     */
    @Test
    public void testRssiBelowMin() {
        ProximityRegion proximityRegion = new ProximityRegion("test proximity region", 1, 2);
        proximityRegion.setRssi(-101);

        // Proximity region should still be valid even if optional properties are improperly set
        assertTrue(proximityRegion.isValid());

        assertNull(proximityRegion.getRssi());
    }

    /**
     * Helper method to create a fixed size string with a repeating character.
     *
     * @param repeat The character to repeat.
     * @param length Length of the String.
     * @return A fixed size string.
     */
    private String createFixedSizeString(char repeat, int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(repeat);
        }

        return builder.toString();
    }
}

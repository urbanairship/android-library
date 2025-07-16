/* Copyright Airship and Contributors */
package com.urbanairship.analytics.location

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ProximityRegionTest {

    /**
     * Test isValid returns true for valid proximity region.
     */
    @Test
    public fun testValidProximityRegion() {
        val proximityRegion = ProximityRegion("valid_proximity_region", 1, 2)
        assertTrue(proximityRegion.isValid)
    }

    /**
     * Test setting a proximity ID above the max allowed character length is invalid.
     */
    @Test
    public fun testProximityIdAboveMax() {
        val proximityId = createFixedSizeString('a', 256)
        val proximityRegion = ProximityRegion(proximityId, 1, 2)
        assertFalse(proximityRegion.isValid)
    }

    /**
     * Test setting a proximity ID below the min allowed character length is invalid.
     */
    @Test
    public fun testProximityIdBelowMin() {
        val proximityRegion = ProximityRegion("", 1, 2)
        assertFalse(proximityRegion.isValid)
    }

    /**
     * Test setting a major above the max allowed is invalid.
     */
    @Test
    public fun testMajorAboveMax() {
        val proximityRegion = ProximityRegion("proximity region test", 65536, 2)
        assertFalse(proximityRegion.isValid)
    }

    /**
     * Test setting a major below the min allowed is invalid.
     */
    @Test
    public fun testMajorBelowMin() {
        val proximityRegion = ProximityRegion("proximity region test", -1, 2)
        assertFalse(proximityRegion.isValid)
    }

    /**
     * Test setting a minor above the max allowed is invalid.
     */
    @Test
    public fun testMinorAboveMax() {
        val proximityRegion = ProximityRegion("proximity region test", 1, 65536)
        assertFalse(proximityRegion.isValid)
    }

    /**
     * Test setting a minor below the min allowed is invalid.
     */
    @Test
    public fun testMinorBelowMin() {
        val proximityRegion = ProximityRegion("proximity region test", 1, -1)
        assertFalse(proximityRegion.isValid)
    }

    /**
     * Test setting a latitude above the max allowed is invalid.
     */
    @Test
    public fun testLatitudeAboveMax() {
        val proximityRegion = ProximityRegion("test proximity region", 1, 2)
        proximityRegion.setCoordinates(91.0, 180.0)

        // Proximity region should still be valid even if optional properties are improperly set
        assertTrue(proximityRegion.isValid)

        assertNull(proximityRegion.latitude)
        assertNull(proximityRegion.longitude)
    }

    /**
     * Test setting a latitude below the min allowed is invalid.
     */
    @Test
    public fun testLatitudeBelowMin() {
        val proximityRegion = ProximityRegion("test proximity region", 1, 2)
        proximityRegion.setCoordinates(-91.0, -180.0)

        // Proximity region should still be valid even if optional properties are improperly set
        assertTrue(proximityRegion.isValid)

        assertNull(proximityRegion.latitude)
        assertNull(proximityRegion.longitude)
    }

    /**
     * Test setting longitude to a valid value then setting it back to null.
     */
    @Test
    public fun testLongitudeNullable() {
        val proximityRegion = ProximityRegion("test proximity region", 1, 2)

        proximityRegion.setCoordinates(0.0, 0.0)
        assertEquals(0.0, proximityRegion.latitude)
        assertEquals(0.0, proximityRegion.longitude)

        proximityRegion.setCoordinates(null, null)
        assertNull(proximityRegion.latitude)
        assertNull(proximityRegion.longitude)
    }

    /**
     * Test setting a longitude above the max allowed is invalid.
     */
    @Test
    public fun testLongitudeAboveMax() {
        val proximityRegion = ProximityRegion("test proximity region", 1, 2)
        proximityRegion.setCoordinates(90.0, 181.0)

        // Proximity region should still be valid even if optional properties are improperly set
        assertTrue(proximityRegion.isValid)

        assertNull(proximityRegion.latitude)
        assertNull(proximityRegion.longitude)
    }

    /**
     * Test setting a longitude below the min allowed is invalid.
     */
    @Test
    public fun testLongitudeBelowMin() {
        val proximityRegion = ProximityRegion("test proximity region", 1, 2)
        proximityRegion.setCoordinates(-90.0, -181.0)

        // Proximity region should still be valid even if optional properties are improperly set
        assertTrue(proximityRegion.isValid)

        assertNull(proximityRegion.latitude)
        assertNull(proximityRegion.longitude)
    }

    /**
     * Test setting rssi to a valid value then setting it back to null.
     */
    @Test
    public fun testRssiNullable() {
        val proximityRegion = ProximityRegion("test proximity region", 1, 2)

        proximityRegion.rssi = -59
        assertEquals(-59, proximityRegion.rssi)

        proximityRegion.rssi = null
        assertNull(proximityRegion.rssi)
    }

    /**
     * Test setting an rssi above the max allowed is invalid.
     */
    @Test
    public fun testRssiAboveMax() {
        val proximityRegion = ProximityRegion("test proximity region", 1, 2)
        proximityRegion.rssi = 101

        // Proximity region should still be valid even if optional properties are improperly set
        assertTrue(proximityRegion.isValid)

        assertNull(proximityRegion.rssi)
    }

    /**
     * Test setting an rssi below the min allowed is invalid.
     */
    @Test
    public fun testRssiBelowMin() {
        val proximityRegion = ProximityRegion("test proximity region", 1, 2)
        proximityRegion.rssi = -101

        // Proximity region should still be valid even if optional properties are improperly set
        assertTrue(proximityRegion.isValid)

        assertNull(proximityRegion.rssi)
    }

    /**
     * Helper method to create a fixed size string with a repeating character.
     *
     * @param repeat The character to repeat.
     * @param length Length of the String.
     * @return A fixed size string.
     */
    private fun createFixedSizeString(repeat: Char, length: Int): String {
        val builder = StringBuilder(length)
        for (i in 0..<length) {
            builder.append(repeat)
        }

        return builder.toString()
    }
}

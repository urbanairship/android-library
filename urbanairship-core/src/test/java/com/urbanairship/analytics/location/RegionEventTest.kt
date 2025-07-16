/* Copyright Airship and Contributors */
package com.urbanairship.analytics.location

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.analytics.ConversionData
import com.urbanairship.analytics.EventTestUtils
import com.urbanairship.analytics.location.RegionEvent.Companion.newBuilder
import com.urbanairship.analytics.location.RegionEvent.Companion.regionEventCharacterCountIsValid
import com.urbanairship.analytics.location.RegionEvent.Companion.regionEventLatitudeIsValid
import com.urbanairship.analytics.location.RegionEvent.Companion.regionEventLongitudeIsValid
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class RegionEventTest {

    /**
     * Test region event data formatting directly.
     */
    @Test
    public fun testRegionEventData() {
        val regionId = "region_id"
        val source = "source"

        val circularRegion = CircularRegion(10.0, 90.0, 180.0)

        val proximityRegion = ProximityRegion("test_proximity_region", 1, 2)
        proximityRegion.setCoordinates(0.0, 0.0)
        proximityRegion.rssi = -59

        val event = newBuilder(regionId, RegionEvent.Boundary.ENTER)
            .setSource(source)
            .setCircularRegion(circularRegion)
            .setProximityRegion(proximityRegion)
            .build()

        val expectedData = JsonValue.parseString(
            """
                {
                  "action": "enter",
                  "circular_region": {
                    "latitude": "90.0000000",
                    "longitude": "180.0000000",
                    "radius": "10.0"
                  },
                  "proximity": {
                    "latitude": "0.0",
                    "longitude": "0.0",
                    "major": 1,
                    "minor": 2,
                    "proximity_id": "test_proximity_region",
                    "rssi": -59
                  },
                  "region_id": "region_id",
                  "source": "source"
                }
            """.trimIndent()
        )

        // test isValid returns true for valid region event
        assertEquals(expectedData.map, event.getEventData(ConversionData(null, null, null)))
    }

    /**
     * Test creating a region event.
     */
    @Test
    public fun testRegionEvent() {
        val regionId = createFixedSizeString('a', 255)
        val source = createFixedSizeString('b', 255)

        val event: RegionEvent = newBuilder(regionId, RegionEvent.Boundary.ENTER)
            .setSource(source)
            .build()

        EventTestUtils.validateEventValue(event, "region_id", regionId)
        EventTestUtils.validateEventValue(event, "source", source)
        EventTestUtils.validateEventValue(event, "action", "enter")

        // test isValid returns true for valid region event
        assertTrue(event.isValid())
    }

    /**
     * Test creating a region event with an empty region ID.
     */
    @Test(expected = IllegalArgumentException::class)
    public fun testEmptyRegionID() {
        val source = createFixedSizeString('b', 255)

        newBuilder("", RegionEvent.Boundary.ENTER)
            .setSource(source)
            .build()
    }

    /**
     * Test creating a region event with a region ID greater than maximum allowed length.
     */
    @Test(expected = IllegalArgumentException::class)
    public fun testMaxRegionID() {
        val regionId = createFixedSizeString('a', 256)
        val source = createFixedSizeString('b', 255)

        newBuilder(regionId, RegionEvent.Boundary.ENTER)
            .setSource(source)
            .build()
    }

    /**
     * Test creating a region event with an empty source.
     */
    @Test(expected = IllegalArgumentException::class)
    public fun testEmptySource() {
        val regionId = createFixedSizeString('a', 255)
        val source = ""

        newBuilder(regionId, RegionEvent.Boundary.ENTER)
            .setSource(source)
            .build()
    }

    /**
     * Test creating a region event with a null source.
     */
    @Test(expected = IllegalArgumentException::class)
    public fun testNullSource() {
        val regionId = createFixedSizeString('b', 255)

        newBuilder(regionId, RegionEvent.Boundary.ENTER).build()
    }

    /**
     * Test creating a region event with a source greater than maximum allowed length.
     */
    @Test(expected = IllegalArgumentException::class)
    public fun testMaxSource() {
        val regionId = createFixedSizeString('a', 255)
        val source = createFixedSizeString('b', 256)

        newBuilder(regionId, RegionEvent.Boundary.ENTER)
            .setSource(source)
            .build()
    }

    /**
     * Test creating a region event and setting a proximity region.
     */
    @Test
    public fun testRegionEventWithProximityRegion() {
        val regionId = createFixedSizeString('a', 255)
        val source = createFixedSizeString('b', 255)

        val proximityRegion = ProximityRegion("test_proximity_region", 1, 2)
        proximityRegion.setCoordinates(0.0, 0.0)
        proximityRegion.rssi = -59

        val event: RegionEvent = newBuilder(regionId, RegionEvent.Boundary.ENTER)
            .setSource(source)
            .setProximityRegion(proximityRegion)
            .build()

        EventTestUtils.validateEventValue(event, "region_id", regionId)
        EventTestUtils.validateEventValue(event, "source", source)
        EventTestUtils.validateEventValue(event, "action", "enter")

        EventTestUtils.validateNestedEventValue(
            event, "proximity", "proximity_id", proximityRegion.proximityId
        )
        EventTestUtils.validateNestedEventValue(
            event, "proximity", "major", proximityRegion.major.toLong()
        )
        EventTestUtils.validateNestedEventValue(
            event, "proximity", "minor", proximityRegion.minor.toLong()
        )
        EventTestUtils.validateNestedEventValue(
            event, "proximity", "latitude", proximityRegion.latitude!!
        )
        EventTestUtils.validateNestedEventValue(
            event, "proximity", "longitude", proximityRegion.longitude!!
        )
        EventTestUtils.validateNestedEventValue(
            event, "proximity", "rssi", proximityRegion.rssi!!.toLong()
        )
    }

    /**
     * Test creating a region event and setting a proximity region without a lat / long.
     */
    @Test
    public fun testRegionEventWithProximityRegionWithNullCoordinates() {
        val regionId = createFixedSizeString('a', 255)
        val source = createFixedSizeString('b', 255)

        val proximityRegion = ProximityRegion("test_proximity_region", 1, 2)
        proximityRegion.rssi = -59

        val event: RegionEvent = newBuilder(regionId, RegionEvent.Boundary.ENTER)
            .setSource(source)
            .setProximityRegion(proximityRegion)
            .build()

        EventTestUtils.validateEventValue(event, "region_id", regionId)
        EventTestUtils.validateEventValue(event, "source", source)
        EventTestUtils.validateEventValue(event, "action", "enter")

        EventTestUtils.validateNestedEventValue(
            event, "proximity", "proximity_id", proximityRegion.proximityId
        )
        EventTestUtils.validateNestedEventValue(
            event, "proximity", "major", proximityRegion.major.toLong()
        )
        EventTestUtils.validateNestedEventValue(
            event, "proximity", "minor", proximityRegion.minor.toLong()
        )

        EventTestUtils.validateNestedEventValue(
            event, "proximity", "rssi", proximityRegion.rssi!!.toLong()
        )
    }

    /**
     * Test creating a region event and setting a proximity region without a set rssi.
     */
    @Test
    public fun testRegionEventWithProximityRegionWithNullRssi() {
        val regionId = createFixedSizeString('a', 255)
        val source = createFixedSizeString('b', 255)

        val proximityRegion = ProximityRegion("test_proximity_region", 1, 2)
        proximityRegion.setCoordinates(0.0, 0.0)

        val event: RegionEvent = newBuilder(regionId, RegionEvent.Boundary.ENTER)
            .setSource(source)
            .setProximityRegion(proximityRegion)
            .build()

        EventTestUtils.validateEventValue(event, "region_id", regionId)
        EventTestUtils.validateEventValue(event, "source", source)
        EventTestUtils.validateEventValue(event, "action", "enter")

        EventTestUtils.validateNestedEventValue(
            event, "proximity", "proximity_id", proximityRegion.proximityId
        )
        EventTestUtils.validateNestedEventValue(
            event, "proximity", "major", proximityRegion.major.toLong()
        )
        EventTestUtils.validateNestedEventValue(
            event, "proximity", "minor", proximityRegion.minor.toLong()
        )
        EventTestUtils.validateNestedEventValue(
            event, "proximity", "latitude", proximityRegion.latitude!!
        )
        EventTestUtils.validateNestedEventValue(
            event, "proximity", "longitude", proximityRegion.longitude!!
        )
    }

    /**
     * Test creating a region event and setting a circular region.
     */
    @Test
    public fun testRegionEventWithCircularRegion() {
        val regionId = createFixedSizeString('a', 255)
        val source = createFixedSizeString('b', 255)

        val circularRegion = CircularRegion(10.0, 90.0, 180.0)

        val event: RegionEvent = newBuilder(regionId, RegionEvent.Boundary.ENTER)
            .setSource(source)
            .setCircularRegion(circularRegion)
            .build()

        EventTestUtils.validateEventValue(event, "region_id", regionId)
        EventTestUtils.validateEventValue(event, "source", source)
        EventTestUtils.validateEventValue(event, "action", "enter")

        EventTestUtils.validateNestedEventValue(event, "circular_region", "radius", 10)
        EventTestUtils.validateNestedEventValue(event, "circular_region", "latitude", 90.0000000)
        EventTestUtils.validateNestedEventValue(event, "circular_region", "longitude", 180.0000000)
    }

    /**
     * Test character count validation directly.
     */
    @Test
    public fun testRegionEventCharacterCountIsValid() {
        val validString = createFixedSizeString('a', 255)
        val invalidStringMax = createFixedSizeString('b', 256)
        val invalidStringMin = ""

        assertTrue(regionEventCharacterCountIsValid(validString))
        assertFalse(regionEventCharacterCountIsValid(invalidStringMax))
        assertFalse(regionEventCharacterCountIsValid(invalidStringMin))
    }

    /**
     * Test region event latitude validation directly.
     */
    @Test
    public fun testRegionEventLatitudeIsValid() {
        val validLatitude = 0.0
        val invalidLatitudeMax = 91.0
        val invalidLatitudeMin = -91.0

        assertTrue(regionEventLatitudeIsValid(validLatitude))
        assertFalse(regionEventLatitudeIsValid(invalidLatitudeMax))
        assertFalse(regionEventLatitudeIsValid(invalidLatitudeMin))
    }

    /**
     * Test region event longitude validation directly.
     */
    @Test
    public fun testRegionEventLongitudeIsValid() {
        val validLongitude = 0.0
        val invalidLongitudeMax = 181.0
        val invalidLongitudeMin = -181.0

        assertTrue(regionEventLongitudeIsValid(validLongitude))
        assertFalse(regionEventLongitudeIsValid(invalidLongitudeMax))
        assertFalse(regionEventLongitudeIsValid(invalidLongitudeMin))
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

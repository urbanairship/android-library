/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.location;

import com.urbanairship.BaseTestCase;
import com.urbanairship.analytics.EventTestUtils;
import com.urbanairship.json.JsonValue;

import org.json.JSONException;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;


public class RegionEventTest extends BaseTestCase {

    /**
     * Test region event data formatting directly.
     */
    @Test
    public void testRegionEventData() throws Exception {

        String regionId = "region_id";
        String source = "source";
        int boundaryEvent = RegionEvent.BOUNDARY_EVENT_ENTER;

        RegionEvent event = new RegionEvent(regionId, source, boundaryEvent);

        CircularRegion circularRegion = new CircularRegion(10, 90.0, 180.0);

        event.setCircularRegion(circularRegion);

        ProximityRegion proximityRegion = new ProximityRegion("test_proximity_region", 1, 2);
        proximityRegion.setCoordinates(0.0, 0.0);
        proximityRegion.setRssi(-59);

        event.setProximityRegion(proximityRegion);

        JsonValue expectedData = JsonValue.parseString("{\"proximity\":{\"proximity_id\":\"test_proximity_region\",\"minor\":2," +
                "\"longitude\":\"0.0\",\"rssi\":-59,\"latitude\":\"0.0\",\"major\":1},\"source\":\"source\"," +
                "\"region_id\":\"region_id\",\"action\":\"enter\",\"circular_region\":{\"radius\":\"10.0\"," +
                "\"longitude\":\"180.0000000\",\"latitude\":\"90.0000000\"}}");

        // test isValid returns true for valid region event
        assertEquals(expectedData.getMap(), event.getEventData());
    }

    /**
     * Test creating a region event.
     */
    @Test
    public void testRegionEvent() throws JSONException {

        String regionId = createFixedSizeString('a', 255);
        String source = createFixedSizeString('b', 255);
        int boundaryEvent = RegionEvent.BOUNDARY_EVENT_ENTER;

        RegionEvent event = new RegionEvent(regionId, source, boundaryEvent);

        EventTestUtils.validateEventValue(event, "region_id", regionId);
        EventTestUtils.validateEventValue(event, "source", source);
        EventTestUtils.validateEventValue(event, "action", "enter");

        // test isValid returns true for valid region event
        assertTrue(event.isValid());
    }

    /**
     * Test creating a region event with an empty region ID.
     */
    @Test
    public void testEmptyRegionID() throws JSONException {

        String regionId = "";
        String source = createFixedSizeString('b', 255);
        int boundaryEvent = RegionEvent.BOUNDARY_EVENT_ENTER;

        RegionEvent event = new RegionEvent(regionId, source, boundaryEvent);

        assertFalse(event.isValid());
        assertNull(event.getEventData());
    }

    /**
     * Test creating a region event with a null region ID.
     */
    @Test
    public void testNullRegionID() throws JSONException {

        String source = createFixedSizeString('b', 255);
        int boundaryEvent = RegionEvent.BOUNDARY_EVENT_ENTER;

        RegionEvent event = new RegionEvent(null, source, boundaryEvent);

        assertFalse(event.isValid());
        assertNull(event.getEventData());
    }

    /**
     * Test creating a region event with a region ID greater than maximum allowed length.
     */
    @Test
    public void testMaxRegionID() throws JSONException {

        String regionId = createFixedSizeString('a', 256);
        String source = createFixedSizeString('b', 255);
        int boundaryEvent = RegionEvent.BOUNDARY_EVENT_ENTER;

        RegionEvent event = new RegionEvent(regionId, source, boundaryEvent);

        assertFalse(event.isValid());
        assertNull(event.getEventData());
    }

    /**
     * Test creating a region event with an empty source.
     */
    @Test
    public void testEmptySource() throws JSONException {

        String regionId = createFixedSizeString('a', 255);
        String source = "";
        int boundaryEvent = RegionEvent.BOUNDARY_EVENT_ENTER;

        RegionEvent event = new RegionEvent(regionId, source, boundaryEvent);

        assertFalse(event.isValid());
        assertNull(event.getEventData());
    }


    /**
     * Test creating a region event with a null source.
     */
    @Test
    public void testNullSource() throws JSONException {

        String regionId = createFixedSizeString('b', 255);
        int boundaryEvent = RegionEvent.BOUNDARY_EVENT_ENTER;

        RegionEvent event = new RegionEvent(regionId, null, boundaryEvent);

        assertFalse(event.isValid());
        assertNull(event.getEventData());
    }

    /**
     * Test creating a region event with a source greater than maximum allowed length.
     */
    @Test
    public void testMaxSource() throws JSONException {

        String regionId = createFixedSizeString('a', 255);
        String source = createFixedSizeString('b', 256);
        int boundaryEvent = RegionEvent.BOUNDARY_EVENT_ENTER;

        RegionEvent event = new RegionEvent(regionId, source, boundaryEvent);

        assertFalse(event.isValid());
        assertNull(event.getEventData());
    }

    /**
     * Test a creating a region event with an invalid boundary event.
     */
    @Test
    public void testInvalidBoundaryEvent() throws JSONException {

        String regionId = createFixedSizeString('a', 255);
        String source = createFixedSizeString('b', 255);
        int boundaryEvent = 11;

        RegionEvent event = new RegionEvent(regionId, source, boundaryEvent);

        assertFalse(event.isValid());
        assertNull(event.getEventData());
    }

    /**
     * Test creating a region event and setting a proximity region.
     */
    @Test
    public void testRegionEventWithProximityRegion() throws JSONException {

        String regionId = createFixedSizeString('a', 255);
        String source = createFixedSizeString('b', 255);
        int boundaryEvent = RegionEvent.BOUNDARY_EVENT_ENTER;

        RegionEvent event = new RegionEvent(regionId, source, boundaryEvent);
        ProximityRegion proximityRegion = new ProximityRegion("test_proximity_region", 1, 2);

        proximityRegion.setCoordinates(0.0, 0.0);
        proximityRegion.setRssi(-59);

        event.setProximityRegion(proximityRegion);

        EventTestUtils.validateEventValue(event, "region_id", regionId);
        EventTestUtils.validateEventValue(event, "source", source);
        EventTestUtils.validateEventValue(event, "action", "enter");

        EventTestUtils.validateNestedEventValue(event, "proximity", "proximity_id", proximityRegion.getProximityId());
        EventTestUtils.validateNestedEventValue(event, "proximity", "major", proximityRegion.getMajor());
        EventTestUtils.validateNestedEventValue(event, "proximity", "minor", proximityRegion.getMinor());
        EventTestUtils.validateNestedEventValue(event, "proximity", "latitude", proximityRegion.getLatitude());
        EventTestUtils.validateNestedEventValue(event, "proximity", "longitude", proximityRegion.getLongitude());
        EventTestUtils.validateNestedEventValue(event, "proximity", "rssi", proximityRegion.getRssi());
    }

    /**
     * Test creating a region event and setting a proximity region without a lat / long.
     */
    @Test
    public void testRegionEventWithProximityRegionWithNullCoordinates() throws JSONException {

        String regionId = createFixedSizeString('a', 255);
        String source = createFixedSizeString('b', 255);
        int boundaryEvent = RegionEvent.BOUNDARY_EVENT_ENTER;

        RegionEvent event = new RegionEvent(regionId, source, boundaryEvent);
        ProximityRegion proximityRegion = new ProximityRegion("test_proximity_region", 1, 2);

        proximityRegion.setRssi(-59);

        event.setProximityRegion(proximityRegion);

        EventTestUtils.validateEventValue(event, "region_id", regionId);
        EventTestUtils.validateEventValue(event, "source", source);
        EventTestUtils.validateEventValue(event, "action", "enter");

        EventTestUtils.validateNestedEventValue(event, "proximity", "proximity_id", proximityRegion.getProximityId());
        EventTestUtils.validateNestedEventValue(event, "proximity", "major", proximityRegion.getMajor());
        EventTestUtils.validateNestedEventValue(event, "proximity", "minor", proximityRegion.getMinor());
        EventTestUtils.validateNestedEventValue(event, "proximity", "rssi", proximityRegion.getRssi());
    }

    /**
     * Test creating a region event and setting a proximity region without a set rssi.
     */
    @Test
    public void testRegionEventWithProximityRegionWithNullRssi() throws JSONException {

        String regionId = createFixedSizeString('a', 255);
        String source = createFixedSizeString('b', 255);
        int boundaryEvent = RegionEvent.BOUNDARY_EVENT_ENTER;

        RegionEvent event = new RegionEvent(regionId, source, boundaryEvent);
        ProximityRegion proximityRegion = new ProximityRegion("test_proximity_region", 1, 2);

        proximityRegion.setCoordinates(0.0, 0.0);

        event.setProximityRegion(proximityRegion);

        EventTestUtils.validateEventValue(event, "region_id", regionId);
        EventTestUtils.validateEventValue(event, "source", source);
        EventTestUtils.validateEventValue(event, "action", "enter");

        EventTestUtils.validateNestedEventValue(event, "proximity", "proximity_id", proximityRegion.getProximityId());
        EventTestUtils.validateNestedEventValue(event, "proximity", "major", proximityRegion.getMajor());
        EventTestUtils.validateNestedEventValue(event, "proximity", "minor", proximityRegion.getMinor());
        EventTestUtils.validateNestedEventValue(event, "proximity", "latitude", proximityRegion.getLatitude());
        EventTestUtils.validateNestedEventValue(event, "proximity", "longitude", proximityRegion.getLongitude());
    }

    /**
     * Test creating a region event and setting a circular region.
     */
    @Test
    public void testRegionEventWithCircularRegion() throws JSONException {

        String regionId = createFixedSizeString('a', 255);
        String source = createFixedSizeString('b', 255);
        int boundaryEvent = RegionEvent.BOUNDARY_EVENT_ENTER;

        RegionEvent event = new RegionEvent(regionId, source, boundaryEvent);

        CircularRegion circularRegion = new CircularRegion(10, 90.0, 180.0);

        event.setCircularRegion(circularRegion);

        EventTestUtils.validateEventValue(event, "region_id", regionId);
        EventTestUtils.validateEventValue(event, "source", source);
        EventTestUtils.validateEventValue(event, "action", "enter");

        EventTestUtils.validateNestedEventValue(event, "circular_region", "radius", 10);
        EventTestUtils.validateNestedEventValue(event, "circular_region", "latitude", 90.0000000);
        EventTestUtils.validateNestedEventValue(event, "circular_region", "longitude", 180.0000000);
    }

    /**
     * Test character count validation directly.
     */
    @Test
    public void testRegionEventCharacterCountIsValid() throws JSONException {
        String validString = createFixedSizeString('a', 255);
        String invalidStringMax = createFixedSizeString('b', 256);
        String invalidStringMin = "";

        assertTrue(RegionEvent.regionEventCharacterCountIsValid(validString));
        assertFalse(RegionEvent.regionEventCharacterCountIsValid(invalidStringMax));
        assertFalse(RegionEvent.regionEventCharacterCountIsValid(invalidStringMin));
    }

    /**
     * Test region event latitude validation directly.
     */
    @Test
    public void testRegionEventLatitudeIsValid() throws JSONException {
        Double validLatitude = 0.0;
        Double invalidLatitudeMax = 91.0;
        Double invalidLatitudeMin = -91.0;

        assertTrue(RegionEvent.regionEventLatitudeIsValid(validLatitude));
        assertFalse(RegionEvent.regionEventLatitudeIsValid(invalidLatitudeMax));
        assertFalse(RegionEvent.regionEventLatitudeIsValid(invalidLatitudeMin));
    }

    /**
     * Test region event longitude validation directly.
     */
    @Test
    public void testRegionEventLongitudeIsValid() throws JSONException {
        Double validLongitude = 0.0;
        Double invalidLongitudeMax = 181.0;
        Double invalidLongitudeMin = -181.0;

        assertTrue(RegionEvent.regionEventLongitudeIsValid(validLongitude));
        assertFalse(RegionEvent.regionEventLongitudeIsValid(invalidLongitudeMax));
        assertFalse(RegionEvent.regionEventLongitudeIsValid(invalidLongitudeMin));
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

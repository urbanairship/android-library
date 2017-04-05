/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import junit.framework.Assert;

import org.json.JSONException;
import org.junit.Test;

public class ScreenTrackingEventTest extends BaseTestCase {


    /**
     * Test screen tracking event data formatting directly
     */
    @Test
    public void testScreenTrackingEventData() throws JsonException {

        String screen = "test_screen";

        ScreenTrackingEvent event = new ScreenTrackingEvent(screen, "previous_screen", 0, 1);

        JsonValue expected = JsonValue.parseString("{\"screen\":\"test_screen\",\"duration\":\"0.001\",\"exited_time\"" +
                ":\"0.001\",\"previous_screen\":\"previous_screen\",\"entered_time\":\"0.000\"}");

        // Test isValid returns true for valid region event with expected data
        Assert.assertEquals(expected.getMap(), event.getEventData());
    }

    /**
     * Test setting screen tracking event screen
     */
    @Test
    public void testSetScreen() throws JSONException {

        String screen = createFixedSizeString('a', 256);

        ScreenTrackingEvent event = new ScreenTrackingEvent(screen, null, 0, 1);

        // Check that 256 character screen is invalid
        Assert.assertFalse(event.isValid());

        screen = createFixedSizeString('a', 255);

        event = new ScreenTrackingEvent(screen, null, 0, 1);

        // Check that 255 character screen is valid
        Assert.assertTrue(event.isValid());

        screen = "";

        event = new ScreenTrackingEvent(screen, null, 0, 1);

        // Check that 0 character screen is invalid
        Assert.assertFalse(event.isValid());
    }

    /**
     * Test setting screen tracking event duration
     */
    @Test
    public void testSetDuration() throws JSONException {

        ScreenTrackingEvent event = new ScreenTrackingEvent("test_screen", null, 0, 1);

        // Check that duration of 1 is valid
        Assert.assertTrue(event.isValid());

        event = new ScreenTrackingEvent("test_screen", null, 0, 0);

        // Check that duration of 0 is valid
        Assert.assertTrue(event.isValid());

        event = new ScreenTrackingEvent("test_screen", null, 0, -1);

        // Check that duration of -1 is invalid
        Assert.assertFalse(event.isValid());
    }

    /**
     * Helper method to create a fixed size string with a repeating character
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



/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import com.urbanairship.json.JsonMap;

import org.json.JSONException;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

public class EventTestUtils {

    /**
     * Verifies an event value.
     *
     * @param event The event to verify.
     * @param key The event's data field to check.
     * @param expectedValue The expected value.
     * @throws org.json.JSONException
     */
    public static void validateEventValue(Event event, String key, String expectedValue) throws JSONException {
        if (expectedValue == null) {
            assertNull("Event's value should not be set.", event.getEventData().get(key));
        } else {
            assertEquals("Event's value for " + key + " is unexpected.", expectedValue, event.getEventData().get(key).getString());
        }
    }

    /**
     * Verifies an event value.
     *
     * @param event The event to verify.
     * @param key The event's data field to check.
     * @param expectedValue The expected value.
     * @throws JSONException
     */
    public static void validateEventValue(Event event, String key, long expectedValue) throws JSONException {
        assertEquals("Event's value for " + key + " is unexpected.", expectedValue, event.getEventData().get(key).getLong(expectedValue - 1));
    }

    /**
     * Verifies an event value.
     *
     * @param event The event to verify.
     * @param key The event's data field to check.
     * @param expectedValue The expected value.
     * @throws JSONException
     */
    public static void validateEventValue(Event event, String key, double expectedValue) throws JSONException {
        assertEquals("Event's value for " + key + " is unexpected.", expectedValue, event.getEventData().get(key).getDouble(expectedValue - 1));
    }

    /**
     * Verifies an event value.
     *
     * @param event The event to verify.
     * @param key The event's data field to check.
     * @param expectedValue The expected value.
     * @throws org.json.JSONException
     */
    public static void validateEventValue(Event event, String key, boolean expectedValue) throws JSONException {
        assertEquals("Event's value for " + key + " is unexpected.", expectedValue, event.getEventData().get(key).getBoolean(!expectedValue));
    }

    /**
     * Verifies a nested event value.
     *
     * @param event The event to verify.
     * @param key The event's data field to check.
     * @param nestedKey the nested data field to check.
     * @param expectedValue The expected value.
     * @throws JSONException
     */
    public static void validateNestedEventValue(Event event, String key, String nestedKey, long expectedValue) throws JSONException {
        long value = event.getEventData().get(key).getMap().get(nestedKey).getLong(expectedValue - 1) == expectedValue ? event.getEventData().get(key).getMap().get(nestedKey).getLong(expectedValue - 1) : Long.valueOf(event.getEventData().get(key).getMap().get(nestedKey).getString().substring(0, event.getEventData().get(key).getMap().get(nestedKey).getString().lastIndexOf(".")));
        assertEquals("Event's value for " + key + " is unexpected.", expectedValue, value);
    }

    /**
     * Verifies a nested event value.
     *
     * @param event The event to verify.
     * @param key The event's data field to check.
     * @param nestedKey the nested data field to check.
     * @param expectedValue The expected value.
     * @throws JSONException
     */
    public static void validateNestedEventValue(Event event, String key, String nestedKey, String expectedValue) throws JSONException {
        String value = event.getEventData().get(key).getMap().get(nestedKey).getString() != null ? event.getEventData().get(key).getMap().get(nestedKey).getString() : event.getEventData().get(key).getMap().get(nestedKey).toString();
        assertEquals("Event's value for " + key + " is unexpected.", expectedValue, value);
    }

    /**
     * Verifies a nested event value.
     *
     * @param event The event to verify.
     * @param key The event's data field to check.
     * @param nestedKey the nested data field to check.
     * @param expectedValue The expected value.
     * @throws JSONException
     */
    public static void validateNestedEventValue(Event event, String key, String nestedKey, boolean expectedValue) throws JSONException {
        assertEquals("Event's value for " + key + " is unexpected.", expectedValue, event.getEventData().get(key).getMap().get(nestedKey).getBoolean(!expectedValue));
    }

    /**
     * Verifies a nested event value.
     *
     * @param event The event to verify.
     * @param key The event's data field to check.
     * @param nestedKey the nested data field to check.
     * @param expectedValue The expected value.
     * @throws JSONException
     */
    public static void validateNestedEventValue(Event event, String key, String nestedKey, double expectedValue) throws JSONException {
        Double value = event.getEventData().get(key).getMap().get(nestedKey).getDouble(expectedValue - 1) == expectedValue ? event.getEventData().get(key).getMap().get(nestedKey).getDouble(expectedValue - 1) : Double.parseDouble(event.getEventData().get(key).getMap().get(nestedKey).getString());
        assertEquals("Event's value for " + key + " is unexpected.", expectedValue, value);
    }

    /**
     * Gets the event's data.
     * @param event The event.
     * @return The event's data.
     */
    public static JsonMap getEventData(Event event) {
        return event.getEventData();
    }
}

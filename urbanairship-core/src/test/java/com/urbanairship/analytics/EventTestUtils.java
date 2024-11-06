/* Copyright Airship and Contributors */

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
     */
    public static void validateEventValue(Event event, String key, String expectedValue) {
        ConversionData conversionData = new ConversionData(null, null, null);
        if (expectedValue == null) {
            assertNull("Event's value should not be set.", event.getEventData(conversionData).get(key));
        } else {
            assertEquals("Event's value for " + key + " is unexpected.", expectedValue, event.getEventData(conversionData).get(key).getString());
        }
    }

    /**
     * Verifies an event value.
     *
     * @param event The event to verify.
     * @param key The event's data field to check.
     * @param expectedValue The expected value.
     */
    public static void validateEventValue(Event event, String key, long expectedValue) {
        ConversionData conversionData = new ConversionData(null, null, null);

        assertEquals("Event's value for " + key + " is unexpected.", expectedValue, event.getEventData(conversionData).get(key).getLong(expectedValue - 1));
    }

    /**
     * Verifies an event value.
     *
     * @param event The event to verify.
     * @param key The event's data field to check.
     * @param expectedValue The expected value.
     */
    public static void validateEventValue(Event event, String key, double expectedValue) {
        ConversionData conversionData = new ConversionData(null, null, null);

        assertEquals("Event's value for " + key + " is unexpected.", expectedValue, event.getEventData(conversionData).get(key).getDouble(expectedValue - 1));
    }

    /**
     * Verifies an event value.
     *
     * @param event The event to verify.
     * @param key The event's data field to check.
     * @param expectedValue The expected value.
     */
    public static void validateEventValue(Event event, String key, boolean expectedValue) {
        ConversionData conversionData = new ConversionData(null, null, null);

        assertEquals("Event's value for " + key + " is unexpected.", expectedValue, event.getEventData(conversionData).get(key).getBoolean(!expectedValue));
    }

    /**
     * Verifies a nested event value.
     *
     * @param event The event to verify.
     * @param key The event's data field to check.
     * @param nestedKey the nested data field to check.
     * @param expectedValue The expected value.
     */
    public static void validateNestedEventValue(Event event, String key, String nestedKey, long expectedValue) {
        ConversionData conversionData = new ConversionData(null, null, null);

        long value = event.getEventData(conversionData).get(key).getMap().get(nestedKey).getLong(expectedValue - 1) == expectedValue ? event.getEventData(conversionData).get(key).getMap().get(nestedKey).getLong(expectedValue - 1) : Long.valueOf(event.getEventData(conversionData).get(key).getMap().get(nestedKey).getString().substring(0, event.getEventData(conversionData).get(key).getMap().get(nestedKey).getString().lastIndexOf(".")));
        assertEquals("Event's value for " + key + " is unexpected.", expectedValue, value);
    }

    /**
     * Verifies a nested event value.
     *
     * @param event The event to verify.
     * @param key The event's data field to check.
     * @param nestedKey the nested data field to check.
     * @param expectedValue The expected value.
     */
    public static void validateNestedEventValue(Event event, String key, String nestedKey, String expectedValue) {
        ConversionData conversionData = new ConversionData(null, null, null);

        String value = event.getEventData(conversionData).get(key).getMap().get(nestedKey).getString() != null ? event.getEventData(conversionData).get(key).getMap().get(nestedKey).getString() : event.getEventData(conversionData).get(key).getMap().get(nestedKey).toString();
        assertEquals("Event's value for " + key + " is unexpected.", expectedValue, value);
    }

    /**
     * Verifies a nested event value.
     *
     * @param event The event to verify.
     * @param key The event's data field to check.
     * @param nestedKey the nested data field to check.
     * @param expectedValue The expected value.
     */
    public static void validateNestedEventValue(Event event, String key, String nestedKey, boolean expectedValue) {
        ConversionData conversionData = new ConversionData(null, null, null);

        assertEquals("Event's value for " + key + " is unexpected.", expectedValue, event.getEventData(conversionData).get(key).getMap().get(nestedKey).getBoolean(!expectedValue));
    }

    /**
     * Verifies a nested event value.
     *
     * @param event The event to verify.
     * @param key The event's data field to check.
     * @param nestedKey the nested data field to check.
     * @param expectedValue The expected value.
     */
    public static void validateNestedEventValue(Event event, String key, String nestedKey, double expectedValue) {
        ConversionData conversionData = new ConversionData(null, null, null);

        Double value = event.getEventData(conversionData).get(key).getMap().get(nestedKey).getDouble(expectedValue - 1) == expectedValue ? event.getEventData(conversionData).get(key).getMap().get(nestedKey).getDouble(expectedValue - 1) : Double.parseDouble(event.getEventData(conversionData).get(key).getMap().get(nestedKey).getString());
        assertEquals("Event's value for " + key + " is unexpected.", expectedValue, value);
    }

    /**
     * Gets the event's data.
     *
     * @param event The event.
     * @return The event's data.
     */
    public static JsonMap getEventData(Event event) {
        ConversionData conversionData = new ConversionData(null, null, null);

        return event.getEventData(conversionData);
    }

}

/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

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

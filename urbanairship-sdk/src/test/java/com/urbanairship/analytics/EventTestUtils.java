/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

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
            assertNull("Event's value should not be set.", event.getEventData().opt(key));
        } else {
            assertEquals("Event's value for " + key + " is unexpected.", expectedValue, event.getEventData().getString(key));
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
        assertEquals("Event's value for " + key + " is unexpected.", expectedValue, event.getEventData().getLong(key));
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
        assertEquals("Event's value for " + key + " is unexpected.", expectedValue, event.getEventData().getDouble(key));
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
        assertEquals("Event's value for " + key + " is unexpected.", expectedValue, event.getEventData().getBoolean(key));
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
        assertEquals("Event's value for " + key + " is unexpected.", expectedValue, event.getEventData().getJSONObject(key).getLong(nestedKey));
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
        assertEquals("Event's value for " + key + " is unexpected.", expectedValue, event.getEventData().getJSONObject(key).getString(nestedKey));
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
        assertEquals("Event's value for " + key + " is unexpected.", expectedValue, event.getEventData().getJSONObject(key).getDouble(nestedKey));
    }

}

/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import com.urbanairship.json.JsonMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

public object EventTestUtils {

    /**
     * Verifies an event value.
     *
     * @param event The event to verify.
     * @param key The event's data field to check.
     * @param expectedValue The expected value.
     */
    public fun validateEventValue(event: Event, key: String, expectedValue: String?) {
        val data = getEventData(event)[key]

        if (expectedValue == null) {
            assertNull("Event's value should not be set.", data)
        } else {
            assertEquals("Event's value for $key is unexpected.", expectedValue, data?.string)
        }
    }

    /**
     * Verifies an event value.
     *
     * @param event The event to verify.
     * @param key The event's data field to check.
     * @param expectedValue The expected value.
     */
    public fun validateEventValue(event: Event, key: String, expectedValue: Long) {
        val data = getEventData(event)[key]

        assertEquals("Event's value for $key is unexpected.",
            expectedValue,
            data?.getLong(expectedValue - 1)
        )
    }

    /**
     * Verifies an event value.
     *
     * @param event The event to verify.
     * @param key The event's data field to check.
     * @param expectedValue The expected value.
     */
    public fun validateEventValue(event: Event, key: String, expectedValue: Double) {
        val data = getEventData(event)[key]

        assertEquals(
            "Event's value for $key is unexpected.",
            expectedValue,
            data?.getDouble(expectedValue - 1)
        )
    }

    /**
     * Verifies an event value.
     *
     * @param event The event to verify.
     * @param key The event's data field to check.
     * @param expectedValue The expected value.
     */
    public fun validateEventValue(event: Event, key: String, expectedValue: Boolean) {
        val data = getEventData(event)[key]

        assertEquals(
            "Event's value for $key is unexpected.",
            expectedValue,
            data?.getBoolean(!expectedValue)
        )
    }

    /**
     * Verifies a nested event value.
     *
     * @param event The event to verify.
     * @param key The event's data field to check.
     * @param nestedKey the nested data field to check.
     * @param expectedValue The expected value.
     */
    public fun validateNestedEventValue(
        event: Event,
        key: String,
        nestedKey: String,
        expectedValue: Long
    ) {

        val nestedValue = getEventData(event)[key]?.map?.get(nestedKey)

        val value = if (nestedValue?.getLong(expectedValue - 1) == expectedValue) {
            nestedValue.getLong(expectedValue - 1)
        } else {
            val string = nestedValue?.string
            string?.substring(0, string.lastIndexOf("."))?.toLong() ?: 0
        }

        assertEquals("Event's value for $key is unexpected.", expectedValue, value)
    }

    /**
     * Verifies a nested event value.
     *
     * @param event The event to verify.
     * @param key The event's data field to check.
     * @param nestedKey the nested data field to check.
     * @param expectedValue The expected value.
     */
    public fun validateNestedEventValue(
        event: Event,
        key: String,
        nestedKey: String,
        expectedValue: String?
    ) {
        val nested = getEventData(event)[key]?.map?.get(nestedKey)

        val value = if (nested?.string != null) {
            nested.string
        } else {
            nested.toString()
        }

        assertEquals("Event's value for $key is unexpected.", expectedValue, value)
    }

    /**
     * Verifies a nested event value.
     *
     * @param event The event to verify.
     * @param key The event's data field to check.
     * @param nestedKey the nested data field to check.
     * @param expectedValue The expected value.
     */
    public fun validateNestedEventValue(
        event: Event,
        key: String,
        nestedKey: String,
        expectedValue: Boolean
    ) {
        val nested = getEventData(event)[key]?.map?.get(nestedKey)?.getBoolean(!expectedValue) ?: !expectedValue

        assertEquals("Event's value for $key is unexpected.", expectedValue, nested)
    }

    /**
     * Verifies a nested event value.
     *
     * @param event The event to verify.
     * @param key The event's data field to check.
     * @param nestedKey the nested data field to check.
     * @param expectedValue The expected value.
     */
    public fun validateNestedEventValue(
        event: Event,
        key: String,
        nestedKey: String,
        expectedValue: Double
    ) {
        val nested = getEventData(event)[key]?.map?.get(nestedKey)

        val value = if (nested?.getDouble(expectedValue - 1) == expectedValue) {
            nested.getDouble(expectedValue - 1)
        } else {
            nested?.string?.toDouble() ?: 0.0
        }
        assertEquals("Event's value for $key is unexpected.", expectedValue, value, 0.00001)
    }

    /**
     * Gets the event's data.
     *
     * @param event The event.
     * @return The event's data.
     */
    public fun getEventData(event: Event): JsonMap {
        val conversionData = ConversionData(null, null, null)
        return event.getEventData(conversionData)
    }
}

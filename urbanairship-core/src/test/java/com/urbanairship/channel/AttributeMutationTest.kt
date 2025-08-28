/* Copyright Airship and Contributors */
package com.urbanairship.channel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Attributes mutation tests.
 */
@RunWith(AndroidJUnit4::class)
public class AttributeMutationTest {

    @Test
    public fun testSetMutation() {
        val mutation = AttributeMutation.newSetAttributeMutation(
            key = "expected_key",
            jsonValue = JsonValue.wrapOpt("expected_value"),
            timestamp = 100
        )

        val expected = jsonMapOf(
            "action" to "set",
            "value" to "expected_value",
            "key" to "expected_key",
            "timestamp" to DateUtils.createIso8601TimeStamp(100)
        )

        assertEquals(expected, mutation.toJsonValue().map)
    }

    @Test
    public fun testRemoveMutation() {
        val mutation = AttributeMutation.newRemoveAttributeMutation("expected_key", 100)

        val expected = jsonMapOf(
            "action" to "remove",
            "key" to "expected_key",
            "timestamp" to DateUtils.createIso8601TimeStamp(100)
        )

        assertEquals(expected, mutation.toJsonValue().map)
    }

    @Test
    public fun testJsonAttributeFromJson() {
        val json = """
            {
              "action": "set",
              "key": "players#bob",
              "timestamp": "2025-04-22T18:05:45",
              "value": {
                "cool_factor": 70,
                "is_cool": true,
                "name": true
              }
            }
        """.trimIndent()

        val mutation = AttributeMutation.fromJsonValue(JsonValue.parseString(json))

        val expectedBody = jsonMapOf(
            "name" to true,
            "cool_factor" to 70,
            "is_cool" to true
        ).toJsonValue()

        val expected = AttributeMutation.newSetAttributeMutation(
            key = "players#bob",
            jsonValue = expectedBody,
            timestamp = DateUtils.parseIso8601("2025-04-22T18:05:45")
        )

        assertEquals(expected, mutation)
    }
}

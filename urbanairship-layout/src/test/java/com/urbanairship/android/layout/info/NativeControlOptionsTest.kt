/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.info

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class NativeControlOptionsTest {

    @Test
    public fun testEmptyObjectFromJson() {
        val json = "{}"

        val fromJson = NativeControlOptions.fromJson(JsonValue.parseString(json))

        assertNull(fromJson.stateRestoration)
    }

    @Test
    public fun testStateRestorationFromJson() {
        val json = """
        {
           "state_restoration": {
              "scope": "instance",
              "restore_id": "restore-1"
           }
        }
        """

        val fromJson = NativeControlOptions.fromJson(JsonValue.parseString(json))
        val expected = NativeControlOptions(
            stateRestoration = NativeControlOptions.StateRestoration(
                scope = NativeControlOptions.StateRestoration.Scope.INSTANCE,
                restoreId = "restore-1"
            )
        )

        assertEquals(expected, fromJson)
    }

    @Test
    public fun testRoundTripFromJsonValue() {
        val json = """
        {
           "state_restoration": {
              "scope": "instance",
              "restore_id": "my-id"
           }
        }
        """

        val parsed = NativeControlOptions.fromJson(JsonValue.parseString(json))
        val again = NativeControlOptions.fromJson(parsed.toJsonValue())

        assertEquals(parsed, again)
    }

    @Test
    public fun testMissingRestoreIdThrows() {
        val json = """
        {
           "state_restoration": {
              "scope": "instance"
           }
        }
        """

        assertThrows(JsonException::class.java) {
            NativeControlOptions.fromJson(JsonValue.parseString(json))
        }
    }

    @Test
    public fun testMissingScopeThrows() {
        val json = """
        {
           "state_restoration": {
              "restore_id": "x"
           }
        }
        """

        assertThrows(JsonException::class.java) {
            NativeControlOptions.fromJson(JsonValue.parseString(json))
        }
    }

    @Test
    public fun testInvalidScopeThrows() {
        val json = """
        {
           "state_restoration": {
              "scope": "session",
              "restore_id": "x"
           }
        }
        """

        assertThrows(JsonException::class.java) {
            NativeControlOptions.fromJson(JsonValue.parseString(json))
        }
    }

    @Test
    public fun testEmptyStateRestorationObjectThrows() {
        val json = """{"state_restoration":{}}"""

        assertThrows(JsonException::class.java) {
            NativeControlOptions.fromJson(JsonValue.parseString(json))
        }
    }
}

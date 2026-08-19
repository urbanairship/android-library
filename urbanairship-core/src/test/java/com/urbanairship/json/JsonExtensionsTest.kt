/* Copyright Airship and Contributors */
package com.urbanairship.json

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class JsonExtensionsTest {

    @Test
    public fun testRequireEpochMillis() {
        val json = jsonMapOf("timestamp" to 1000L)

        assertEquals(Instant.ofEpochMilli(1000), json.requireEpochMillis("timestamp"))
    }

    @Test
    public fun testRequireEpochMillisThrowsOnMissingField() {
        assertThrows(JsonException::class.java) {
            jsonMapOf().requireEpochMillis("timestamp")
        }
    }

    /** A required timestamp must reject a malformed value rather than silently defaulting. */
    @Test
    public fun testRequireEpochMillisThrowsOnNonNumberField() {
        val json = jsonMapOf("timestamp" to "not a number")

        assertThrows(JsonException::class.java) {
            json.requireEpochMillis("timestamp")
        }
    }

    @Test
    public fun testOptionalEpochMillis() {
        val json = jsonMapOf("timestamp" to 1000L)

        assertEquals(Instant.ofEpochMilli(1000), json.optionalEpochMillis("timestamp"))
    }

    @Test
    public fun testOptionalEpochMillisIsNullWhenAbsent() {
        assertNull(jsonMapOf().optionalEpochMillis("timestamp"))
    }

    /**
     * The optional reader is lenient, matching [optionalField]: a value that is present but not
     * a usable timestamp reads as absent rather than throwing.
     *
     * A present-and-null field has to be built via the [JsonMap] constructor — both
     * `JsonMap.Builder` and the JSON parser drop nulls — so this case is not reachable from a
     * parsed payload. It is covered to keep the behavior uniform with [optionalField].
     */
    @Test
    public fun testOptionalEpochMillisIsNullWhenExplicitlyNull() {
        val json = JsonMap(mapOf("timestamp" to JsonValue.NULL))
        assertTrue("expected a present, null field", json.containsKey("timestamp"))

        assertNull(json.optionalEpochMillis("timestamp"))
    }

    @Test
    public fun testOptionalEpochMillisIsNullWhenNotANumber() {
        val json = jsonMapOf("timestamp" to "not a number")

        assertNull(json.optionalEpochMillis("timestamp"))
    }

    /**
     * `requireField`/`optionalField` deliberately have no `Instant` branch: the encoded unit
     * varies by field, so a read has to name it. Reading one as an `Instant` is an error rather
     * than an assumption that the field holds milliseconds.
     */
    @Test
    public fun testReifiedFieldReadersRejectInstant() {
        val json = jsonMapOf("timestamp" to 1000L)

        assertThrows(JsonException::class.java) { json.requireField<Instant>("timestamp") }
        assertThrows(JsonException::class.java) { json.optionalField<Instant>("timestamp") }
    }

    @Test
    public fun testRequireEpochMillisRoundTrip() {
        val instant = Instant.ofEpochMilli(1_700_000_000_123L)

        assertEquals(instant, JsonValue.wrap(instant.toEpochMilli()).requireEpochMillis())
    }

    @Test
    public fun testRequireEpochMillisThrowsOnNonNumber() {
        assertThrows(JsonException::class.java) {
            JsonValue.wrap("nope").requireEpochMillis()
        }
    }
}

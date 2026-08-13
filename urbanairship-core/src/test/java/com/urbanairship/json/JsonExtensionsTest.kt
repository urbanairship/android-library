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
    public fun testRequireFieldInstantReadsEpochMillis() {
        val json = jsonMapOf("timestamp" to 1000L)

        assertEquals(Instant.ofEpochMilli(1000), json.requireField<Instant>("timestamp"))
    }

    @Test
    public fun testRequireFieldInstantThrowsOnMissing() {
        assertThrows(JsonException::class.java) {
            jsonMapOf().requireField<Instant>("timestamp")
        }
    }

    /** A required timestamp must reject a malformed value rather than silently defaulting. */
    @Test
    public fun testRequireFieldInstantThrowsOnNonNumber() {
        val json = jsonMapOf("timestamp" to "not a number")

        assertThrows(JsonException::class.java) {
            json.requireField<Instant>("timestamp")
        }
    }

    @Test
    public fun testOptionalFieldInstantReadsEpochMillis() {
        val json = jsonMapOf("timestamp" to 1000L)

        assertEquals(Instant.ofEpochMilli(1000), json.optionalField<Instant>("timestamp"))
    }

    @Test
    public fun testOptionalFieldInstantIsNullWhenAbsent() {
        assertNull(jsonMapOf().optionalField<Instant>("timestamp"))
    }

    /**
     * The optional reader is lenient, matching every other type it handles: a value that is
     * present but not a usable timestamp reads as absent rather than throwing.
     *
     * A present-and-null field has to be built via the [JsonMap] constructor — both
     * `JsonMap.Builder` and the JSON parser drop nulls — so this case is not reachable from a
     * parsed payload. It is covered to keep the branch uniform with its peers.
     */
    @Test
    public fun testOptionalFieldInstantIsNullWhenExplicitlyNull() {
        val json = JsonMap(mapOf("timestamp" to JsonValue.NULL))
        assertTrue("expected a present, null field", json.containsKey("timestamp"))

        assertNull(json.optionalField<Instant>("timestamp"))
    }

    @Test
    public fun testOptionalFieldInstantIsNullWhenNotANumber() {
        val json = jsonMapOf("timestamp" to "not a number")

        assertNull(json.optionalField<Instant>("timestamp"))
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

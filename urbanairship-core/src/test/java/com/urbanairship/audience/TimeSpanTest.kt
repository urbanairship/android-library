/* Copyright Airship and Contributors */

package com.urbanairship.audience

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import java.time.Instant
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class TimeSpanTest {

    @Test
    public fun testBoundedIsActiveStartInclusiveEndExclusive() {
        val span = TimeSpan(startTimestamp = Instant.ofEpochMilli(100), endTimestamp = Instant.ofEpochMilli(200))

        assertFalse(span.isActive(Instant.ofEpochMilli(99)))
        assertTrue(span.isActive(Instant.ofEpochMilli(100)))
        assertTrue(span.isActive(Instant.ofEpochMilli(150)))
        assertTrue(span.isActive(Instant.ofEpochMilli(199)))
        // End is exclusive.
        assertFalse(span.isActive(Instant.ofEpochMilli(200)))
        assertFalse(span.isActive(Instant.ofEpochMilli(201)))
    }

    @Test
    public fun testNullStartIsNegativeInfinity() {
        val span = TimeSpan(startTimestamp = null, endTimestamp = Instant.ofEpochMilli(200))

        assertTrue(span.isActive(Instant.MIN))
        assertTrue(span.isActive(Instant.ofEpochMilli(199)))
        assertFalse(span.isActive(Instant.ofEpochMilli(200)))
    }

    @Test
    public fun testNullEndIsPositiveInfinity() {
        val span = TimeSpan(startTimestamp = Instant.ofEpochMilli(100), endTimestamp = null)

        assertFalse(span.isActive(Instant.ofEpochMilli(99)))
        assertTrue(span.isActive(Instant.ofEpochMilli(100)))
        assertTrue(span.isActive(Instant.MAX))
    }

    @Test
    public fun testNullBoundsAlwaysActive() {
        val span = TimeSpan(startTimestamp = null, endTimestamp = null)

        assertTrue(span.isActive(Instant.MIN))
        assertTrue(span.isActive(Instant.ofEpochMilli(0)))
        assertTrue(span.isActive(Instant.MAX))
    }

    @Test
    public fun testJsonRoundTrip() {
        val span = TimeSpan(startTimestamp = Instant.ofEpochMilli(100), endTimestamp = Instant.ofEpochMilli(200))
        val parsed = TimeSpan.fromJson(span.toJsonValue().requireMap())
        assertEquals(span, parsed)
    }

    @Test
    public fun testJsonOptionalFields() {
        val parsed = TimeSpan.fromJson(JsonValue.parseString("{}").requireMap())
        assertEquals(TimeSpan(startTimestamp = null, endTimestamp = null), parsed)
    }
}

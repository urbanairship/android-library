/* Copyright Airship and Contributors */

package com.urbanairship.audience

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class TimeSpanTest {

    @Test
    public fun testBoundedIsActiveStartInclusiveEndExclusive() {
        val span = TimeSpan(startTimestamp = 100, endTimestamp = 200)

        assertFalse(span.isActive(99))
        assertTrue(span.isActive(100))
        assertTrue(span.isActive(150))
        assertTrue(span.isActive(199))
        // End is exclusive.
        assertFalse(span.isActive(200))
        assertFalse(span.isActive(201))
    }

    @Test
    public fun testNullStartIsNegativeInfinity() {
        val span = TimeSpan(startTimestamp = null, endTimestamp = 200)

        assertTrue(span.isActive(Long.MIN_VALUE))
        assertTrue(span.isActive(199))
        assertFalse(span.isActive(200))
    }

    @Test
    public fun testNullEndIsPositiveInfinity() {
        val span = TimeSpan(startTimestamp = 100, endTimestamp = null)

        assertFalse(span.isActive(99))
        assertTrue(span.isActive(100))
        assertTrue(span.isActive(Long.MAX_VALUE))
    }

    @Test
    public fun testNullBoundsAlwaysActive() {
        val span = TimeSpan(startTimestamp = null, endTimestamp = null)

        assertTrue(span.isActive(Long.MIN_VALUE))
        assertTrue(span.isActive(0))
        assertTrue(span.isActive(Long.MAX_VALUE))
    }

    @Test
    public fun testJsonRoundTrip() {
        val span = TimeSpan(startTimestamp = 100, endTimestamp = 200)
        val parsed = TimeSpan.fromJson(span.toJsonValue().requireMap())
        assertEquals(span, parsed)
    }

    @Test
    public fun testJsonOptionalFields() {
        val parsed = TimeSpan.fromJson(JsonValue.parseString("{}").requireMap())
        assertEquals(TimeSpan(startTimestamp = null, endTimestamp = null), parsed)
    }
}

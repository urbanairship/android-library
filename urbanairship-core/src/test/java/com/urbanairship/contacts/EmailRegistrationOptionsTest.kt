/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class EmailRegistrationOptionsTest {

    @Test
    public fun testRoundTrip() {
        val options = EmailRegistrationOptions.commercialOptions(
            transactionalOptedIn = Instant.ofEpochMilli(1000),
            commercialOptedIn = Instant.ofEpochMilli(2000),
            properties = jsonMapOf("key" to "value")
        )

        val restored = EmailRegistrationOptions.fromJson(options.toJsonValue())

        assertEquals(Instant.ofEpochMilli(1000), restored.transactionalOptedIn)
        assertEquals(Instant.ofEpochMilli(2000), restored.commercialOptedIn)
        assertEquals(options, restored)
    }

    @Test
    public fun testUnsetOptInsRoundTripAsNull() {
        val options = EmailRegistrationOptions.commercialOptions()

        val restored = EmailRegistrationOptions.fromJson(options.toJsonValue())

        assertNull(restored.transactionalOptedIn)
        assertNull(restored.commercialOptedIn)
    }

    /**
     * Options persisted by older SDK versions encoded "unset" as `-1` rather than omitting the
     * field, and the upload path filtered on `> 0`. Reading `-1` back as a real timestamp would
     * register an opt-in dated just before the epoch that the user never gave, so any
     * non-positive value must still read as unset.
     */
    @Test
    public fun testLegacyNegativeOneSentinelReadsAsUnset() {
        val legacy = jsonMapOf(
            "transactional_opted_in" to -1L,
            "commercial_opted_in" to -1L,
            "double_opt_in" to false
        ).toJsonValue()

        val restored = EmailRegistrationOptions.fromJson(legacy)

        assertNull(restored.transactionalOptedIn)
        assertNull(restored.commercialOptedIn)
    }

    @Test
    public fun testZeroSentinelReadsAsUnset() {
        val legacy = jsonMapOf(
            "transactional_opted_in" to 0L,
            "commercial_opted_in" to 0L,
            "double_opt_in" to false
        ).toJsonValue()

        val restored = EmailRegistrationOptions.fromJson(legacy)

        assertNull(restored.transactionalOptedIn)
        assertNull(restored.commercialOptedIn)
    }

    @Test
    public fun testMissingFieldsReadAsUnset() {
        val restored = EmailRegistrationOptions.fromJson(JsonValue.wrap(jsonMapOf()))

        assertNull(restored.transactionalOptedIn)
        assertNull(restored.commercialOptedIn)
    }
}

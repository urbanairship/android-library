/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AssociateIdentifiersEventTest {

    @Test
    public fun testEventType() {
        val event = AssociateIdentifiersEvent(AssociatedIdentifiers())
        assertEquals(event.type.reportingName, "associate_identifiers")
    }

    /**
     * Test the event is invalid when the associated identifiers exceed 100.
     */
    @Test
    public fun testInvalidEvent() {

        val ids = (0..100)
            .associate { UUID.randomUUID().toString() to "value" }

        // Verify its invalid
        assertFalse(AssociateIdentifiersEvent(AssociatedIdentifiers(ids)).isValid())
    }

    /**
     * Test the event is valid if it contains 0 to 100 ids.
     */
    @Test
    public fun testValidEvent() {
        // Verify 0 Ids is valid
        assertTrue(AssociateIdentifiersEvent(AssociatedIdentifiers()).isValid())

        // Add 100
        val ids = (0..99)
            .associate { UUID.randomUUID().toString() to "value" }

        // Verify 100 Ids is valid
        assertTrue(AssociateIdentifiersEvent(AssociatedIdentifiers(ids)).isValid())
    }

    /**
     * Test event data when limited ad tracking enabled.
     */
    @Test
    public fun testEventData() {
        val ids = mapOf(
            "com.urbanairship.aaid" to "advertising Id",
            "phone" to "867-5309",
            "com.urbanairship.limited_ad_tracking_enabled" to "true"
        )

        val event = AssociateIdentifiersEvent(AssociatedIdentifiers(ids))
        EventTestUtils.validateEventValue(event, "com.urbanairship.aaid", "advertising Id")
        EventTestUtils.validateEventValue(event, "phone", "867-5309")
        EventTestUtils.validateEventValue(
            event, "com.urbanairship.limited_ad_tracking_enabled", "true"
        )
    }

    /**
     * Test event data when limited ad tracking disabled.
     */
    @Test
    public fun testEventDataWithLimitedTrackingDisabled() {
        val ids = mapOf(
            "com.urbanairship.aaid" to "advertising Id",
            "phone" to "867-5309",
            "com.urbanairship.limited_ad_tracking_enabled" to "false"
        )

        val event = AssociateIdentifiersEvent(AssociatedIdentifiers(ids))
        EventTestUtils.validateEventValue(event, "com.urbanairship.aaid", "advertising Id")
        EventTestUtils.validateEventValue(event, "phone", "867-5309")
        EventTestUtils.validateEventValue(
            event, "com.urbanairship.limited_ad_tracking_enabled", "false"
        )
    }
}

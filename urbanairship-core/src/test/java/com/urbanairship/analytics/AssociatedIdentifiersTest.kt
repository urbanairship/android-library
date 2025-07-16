/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AssociatedIdentifiersTest {

    // Set by the AssociatedIdentifiers editor onApply
    private var clear: Boolean? = null
    private var idsToAdd: Map<String, String>? = null
    private var idsToRemove: List<String>? = null

    private var identifiers = AssociatedIdentifiers()
    private var editor: AssociatedIdentifiers.Editor = object : AssociatedIdentifiers.Editor() {
        override fun onApply(
            clear: Boolean,
            idsToAdd: Map<String, String>,
            idsToRemove: List<String>
        ) {
            this@AssociatedIdentifiersTest.clear = clear
            this@AssociatedIdentifiersTest.idsToAdd = idsToAdd
            this@AssociatedIdentifiersTest.idsToRemove = idsToRemove
        }
    }

    /**
     * Test the ID mapping
     */
    @Test
    public fun testIds() {
        val ids = mapOf(
            "com.urbanairship.aaid" to "advertising ID",
            "com.urbanairship.limited_ad_tracking_enabled" to "true",
            "custom key" to "custom value"
        )

        val identifiers = AssociatedIdentifiers(ids)

        assertEquals(identifiers.ids.size, 3)
        assertEquals("custom value", identifiers.ids["custom key"])
        assertEquals(
            identifiers.advertisingId, identifiers.ids["com.urbanairship.aaid"]
        )
        assertTrue(identifiers.isLimitAdTrackingEnabled)
    }

    /**
     * Test setAdvertisingId
     */
    @Test
    public fun testSetAdvertisingId() {
        editor
            .setAdvertisingId("advertising Id", true)
            .addIdentifier("custom key", "custom value")
            .apply()

        assertEquals(3, idsToAdd?.size)
        assertEquals("advertising Id", idsToAdd?.get("com.urbanairship.aaid"))
        assertEquals(
            "true", idsToAdd?.get("com.urbanairship.limited_ad_tracking_enabled")
        )
        assertEquals("custom value", idsToAdd?.get("custom key"))

        assertEquals(0, idsToRemove?.size)
        assertTrue(clear == false)
    }

    /**
     * Test the ID mapping when limit ad tracking is disabled
     */
    @Test
    public fun testLimitAdTrackingDisabled() {
        editor
            .setAdvertisingId("advertising Id", false)
            .addIdentifier("custom key", "custom value")
            .apply()

        assertEquals(3, idsToAdd?.size)
        assertEquals("advertising Id", idsToAdd?.get("com.urbanairship.aaid"))
        assertEquals(
            "false", idsToAdd?.get("com.urbanairship.limited_ad_tracking_enabled")
        )
        assertEquals("custom value", idsToAdd?.get("custom key"))

        assertEquals(0, idsToRemove?.size)
        assertTrue(clear == false)
    }

    /**
     * Test removing advertising ID (and limitedAdTrackingEnabled)
     */
    @Test
    public fun testRemoveAdvertisingId() {
        editor.removeAdvertisingId().apply()

        assertEquals(0, idsToAdd?.size)
        assertEquals(2, idsToRemove?.size)
        assert(idsToRemove?.contains("com.urbanairship.aaid") == true)
        assert(idsToRemove?.contains("com.urbanairship.limited_ad_tracking_enabled") == true)
        assertTrue(clear == false)
    }

    /**
     * Test remove identifier
     */
    @Test
    public fun testRemoveIdentifier() {
        editor.removeIdentifier("custom key").apply()

        assertEquals(0, idsToAdd?.size)
        assertEquals(1, idsToRemove?.size)
        assert(idsToRemove?.contains("custom key") == true)
        assertTrue(clear == false)
    }

    /**
     * Test clear all identifiers
     */
    @Test
    public fun testClearIdentifiers() {
        editor.clear().apply()

        assertEquals(0, idsToAdd?.size)
        assertEquals(0, idsToRemove?.size)
        assertTrue(clear == true)
    }
}

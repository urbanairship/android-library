/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AppBackgroundEventTest {

    @Test
    public fun testEventData() {
        val event = AppBackgroundEvent(100)

        val conversionData = ConversionData("send id", " send metadata", "last metadata")
        val eventData = event.getEventData(conversionData)

        assertEquals(
            eventData.require(Event.PUSH_ID_KEY).optString(),
            conversionData.conversionSendId
        )
        assertEquals(
            eventData.require(Event.METADATA_KEY).optString(),
            conversionData.conversionMetadata
        )
    }
}

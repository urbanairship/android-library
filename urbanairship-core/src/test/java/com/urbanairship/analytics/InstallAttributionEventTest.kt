/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import com.urbanairship.BaseTestCase
import com.urbanairship.analytics.EventTestUtils.validateEventValue
import org.junit.Assert.assertEquals
import org.junit.Test

public class InstallAttributionEventTest public constructor() : BaseTestCase() {

    @Test
    public fun testData() {
        val event = InstallAttributionEvent("referrer")
        validateEventValue(event, "google_play_referrer", "referrer")
    }

    @Test
    public fun testType() {
        val event = InstallAttributionEvent("referrer")
        assertEquals("install_attribution", event.type.reportingName)
    }
}

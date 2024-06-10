/* Copyright Airship and Contributors */

package com.urbanairship.featureflag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.analytics.Event
import com.urbanairship.analytics.EventType
import com.urbanairship.json.jsonMapOf
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeatureFlagInteractionEventTest {

    @Test
    fun testEvent() {
        val flag = FeatureFlag.createFlag(
            name = "some-flag-name",
            isEligible = true,
            reportingInfo = FeatureFlag.ReportingInfo(
                reportingMetadata = jsonMapOf("reporting" to "is good"),
                channelId = "some channel",
                contactId = "some contact"
            )
        )

        val expectedData = jsonMapOf(
            "flag_name" to "some-flag-name",
            "eligible" to true,
            "reporting_metadata" to jsonMapOf("reporting" to "is good"),
            "device" to jsonMapOf(
                "channel_id" to "some channel",
                "contact_id" to "some contact"
            )
        )

        val event = FeatureFlagInteractionEvent(flag)
        assert(event.type.reportingName == "feature_flag_interaction")
        assert(event.type == EventType.FEATURE_FLAG_INTERACTION)
        assert(event.priority == Event.NORMAL_PRIORITY)
        assert(event.data == expectedData)
    }

    @Test
    fun testCreateFlag() {
        val reportingInfo = FeatureFlag.ReportingInfo(
            reportingMetadata = jsonMapOf("reporting" to "is good"),
            channelId = "some channel",
            contactId = "some contact"
        )

        val flag = FeatureFlag.createFlag(
            name = "some-flag-name",
            isEligible = true,
            reportingInfo = reportingInfo,
            variables = jsonMapOf("variables" to "are cool")
        )

        assert(flag.name == "some-flag-name")
        assert(flag.exists)
        assert(flag.isEligible)
        assert(flag.variables == jsonMapOf("variables" to "are cool"))
        assert(flag.reportingInfo == reportingInfo)
    }

    @Test(expected = Exception::class)
    fun testEventMissingReportingInfo() {
        val flag = FeatureFlag.createMissingFlag("some-flag")
        FeatureFlagInteractionEvent(flag)
    }
}

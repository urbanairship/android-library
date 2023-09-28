/* Copyright Airship and Contributors */

package com.urbanairship.featureflag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.jsonMapOf
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeatureFlagTest {

    @Test
    fun testCreateMissingFlag() {
        val flag = FeatureFlag.createMissingFlag("some-flag-name")

        assert(flag.name == "some-flag-name")
        assert(!flag.exists)
        assert(!flag.isEligible)
        assert(flag.variables == null)
        assert(flag.reportingInfo == null)
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

    @Test
    fun testJson() {
        val flag = FeatureFlag.createFlag(
            name = "some-flag-name",
            isEligible = true,
            reportingInfo = FeatureFlag.ReportingInfo(
                reportingMetadata = jsonMapOf("reporting" to "is good"),
                channelId = "some channel",
                contactId = "some contact"
            ),
            variables = jsonMapOf("variables" to "are cool")
        )

        val expectedJson = jsonMapOf(
            "name" to "some-flag-name",
            "is_eligible" to true,
            "exists" to true,
            "_reporting_info" to jsonMapOf(
                "reporting_metadata" to jsonMapOf("reporting" to "is good"),
                "channel_id" to "some channel",
                "contact_id" to "some contact"
            ),
            "variables" to jsonMapOf("variables" to "are cool")
        )

        assert(expectedJson == flag.toJsonValue().requireMap())
        assert(FeatureFlag.fromJson(expectedJson.toJsonValue()) == flag)
    }

    @Test
    fun testJsonDeprecatedConstructor() {
        val flag = FeatureFlag(isEligible = false, exists = false, variables = null)
        val expectedJson = jsonMapOf(
            "name" to "",
            "is_eligible" to false,
            "exists" to false
        )

        assert(expectedJson == flag.toJsonValue().requireMap())
        assert(FeatureFlag.fromJson(expectedJson.toJsonValue()) == flag)
    }

    @Test
    fun testJsonMissingFlag() {
        val flag = FeatureFlag.createMissingFlag("some-flag")
        val expectedJson = jsonMapOf(
            "name" to "some-flag",
            "is_eligible" to false,
            "exists" to false
        )

        assert(expectedJson == flag.toJsonValue().requireMap())
        assert(FeatureFlag.fromJson(expectedJson.toJsonValue()) == flag)
    }
}

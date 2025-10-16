/* Copyright Airship and Contributors */

package com.urbanairship.featureflag

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.analytics.AirshipEventFeed
import com.urbanairship.analytics.Analytics
import com.urbanairship.analytics.ConversionData
import com.urbanairship.json.jsonMapOf
import java.util.UUID
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class FeatureFlagAnalyticsTest {

    private val analytics: Analytics = mockk()
    private val feed: AirshipEventFeed = mockk(relaxed = true)
    private val featureFlagAnalytics = FeatureFlagAnalytics(analytics)
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    public fun testTrackInteraction(): TestResult = runTest {
        coEvery { analytics.addEvent(any()) } returns true

        val flag = FeatureFlag.createFlag("some-flag", true, generateReportingInfo())
        featureFlagAnalytics.trackInteraction(flag)

        verify {
            analytics.addEvent(withArg {
                // The event has a different time stamp so we are just comparing the data
                assert(it.getEventData(context, ConversionData()) == FeatureFlagInteractionEvent(flag).data)
            })
        }
    }

    @Test
    public fun testTrackInteractionSupersede(): TestResult = runTest {
        coEvery { analytics.addEvent(any()) } returns true

        val flag = FeatureFlag.createFlag(
            name = "supersede-flag",
            isEligible = true,
            reportingInfo = FeatureFlag.ReportingInfo(
                reportingMetadata = jsonMapOf("original" to "reporting-metadata"),
                supersededReportingMetadata = listOf(jsonMapOf("superseded" to "metadata")),
                channelId = "channel-id",
                contactId = "contact-id"
            )
        )

        featureFlagAnalytics.trackInteraction(flag)

        verify {
            analytics.addEvent(withArg {
                assertEquals(
                    jsonMapOf(
                        "flag_name" to "supersede-flag",
                        "eligible" to true,
                        "reporting_metadata" to jsonMapOf("original" to "reporting-metadata"),
                        "superseded_reporting_metadata" to listOf(jsonMapOf("superseded" to "metadata")),
                        "device" to jsonMapOf(
                            "channel_id" to "channel-id",
                            "contact_id" to "contact-id",
                        )
                    ),
                    it.getEventData(context, ConversionData())
                )
            })
        }
    }

    @Test
    public fun testTrackInteractionFailed(): TestResult = runTest {
        coEvery { analytics.addEvent(any()) } returns false

        val flag = FeatureFlag.createFlag("some-flag", true, generateReportingInfo())
        featureFlagAnalytics.trackInteraction(flag)

        verify {
            analytics.addEvent(withArg {
                // The event has a different time stamp so we are just comparing the data
                assert(it.getEventData(context, ConversionData()) == FeatureFlagInteractionEvent(flag).data)
            })
        }
    }

    @Test
    public fun testTrackInteractionFlagDoesNotExist(): TestResult = runTest {
        val flag = FeatureFlag.createMissingFlag("some-flag")
        featureFlagAnalytics.trackInteraction(flag)

        verify(exactly = 0) {
            analytics.addEvent(any())
        }
    }

    @Test
    public fun testTrackInteractionMissingReportingInfo(): TestResult = runTest {
        val flag = FeatureFlag(true, true,  null)
        featureFlagAnalytics.trackInteraction(flag)

        verify(exactly = 0) {
            analytics.addEvent(any())
        }
    }

    private fun generateReportingInfo(): FeatureFlag.ReportingInfo {
        return FeatureFlag.ReportingInfo(
            reportingMetadata = jsonMapOf("flag_id" to UUID.randomUUID().toString()),
            contactId = UUID.randomUUID().toString(),
            channelId =  UUID.randomUUID().toString()
        )
    }
}

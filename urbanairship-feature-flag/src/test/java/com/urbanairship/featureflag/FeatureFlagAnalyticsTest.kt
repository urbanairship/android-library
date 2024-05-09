/* Copyright Airship and Contributors */

package com.urbanairship.featureflag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.analytics.AirshipEventFeed
import com.urbanairship.analytics.Analytics
import com.urbanairship.json.jsonMapOf
import java.util.UUID
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeatureFlagAnalyticsTest {

    private val analytics: Analytics = mockk()
    private val feed: AirshipEventFeed = mockk(relaxed = true)
    private val featureFlagAnalytics = FeatureFlagAnalytics(feed, analytics)

    @Test
    fun testTrackInteraction(): TestResult = runTest {
        coEvery { analytics.addEvent(any()) } returns true

        val flag = FeatureFlag.createFlag("some-flag", true, generateReportingInfo())
        featureFlagAnalytics.trackInteraction(flag)

        verify {
            analytics.addEvent(withArg {
                // The event has a different time stamp so we are just comparing the data
                assert(it.eventData == FeatureFlagInteractionEvent(flag).data)
            })

            feed.emit(
                AirshipEventFeed.Event.FeatureFlagInteracted(FeatureFlagInteractionEvent(flag).data)
            )
        }
    }

    @Test
    fun testTrackInteractionFailed(): TestResult = runTest {
        coEvery { analytics.addEvent(any()) } returns false

        val flag = FeatureFlag.createFlag("some-flag", true, generateReportingInfo())
        featureFlagAnalytics.trackInteraction(flag)

        verify {
            analytics.addEvent(withArg {
                // The event has a different time stamp so we are just comparing the data
                assert(it.eventData == FeatureFlagInteractionEvent(flag).data)
            })
        }
        verify(exactly = 0) { feed.emit(any()) }
    }

    @Test
    fun testTrackInteractionFlagDoesNotExist(): TestResult = runTest {
        val flag = FeatureFlag.createMissingFlag("some-flag")
        featureFlagAnalytics.trackInteraction(flag)

        verify(exactly = 0) {
            analytics.addEvent(any())
            feed.emit(any())
        }
    }

    @Test
    fun testTrackInteractionMissingReportingInfo(): TestResult = runTest {
        val flag = FeatureFlag(true, true,  null)
        featureFlagAnalytics.trackInteraction(flag)

        verify(exactly = 0) {
            analytics.addEvent(any())
            feed.emit(any())
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

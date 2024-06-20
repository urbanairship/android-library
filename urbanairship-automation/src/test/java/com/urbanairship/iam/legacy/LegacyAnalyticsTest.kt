package com.urbanairship.iam.legacy

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.iam.analytics.InAppEventMessageId
import com.urbanairship.iam.analytics.InAppEventRecorderInterface
import com.urbanairship.json.jsonMapOf
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class LegacyAnalyticsTest {
    private val recorder: InAppEventRecorderInterface = mockk(relaxed = true)
    private val analytics = LegacyAnalytics(recorder)

    @Test
    public fun testDirectOpen() {
        analytics.recordDirectOpenEvent("some-schedule-id")

        verify {
            recorder.recordEvent(match {
                val expectedData = jsonMapOf(
                    "type" to "direct_open"
                )

                it.event.data == expectedData &&
                        it.event.eventType.reportingName == "in_app_resolution" &&
                        it.messageId == InAppEventMessageId.Legacy("some-schedule-id")
            })
        }
    }

    @Test
    public fun testReplaced() {
        analytics.recordReplacedEvent("some-schedule-id", "some-other-schedule-id")

        verify {
            recorder.recordEvent(match {
                val expectedData = jsonMapOf(
                    "type" to "replaced",
                    "replacement_id" to "some-other-schedule-id"
                )

                it.event.data == expectedData && it.event.eventType.reportingName == "in_app_resolution" && it.messageId == InAppEventMessageId.Legacy(
                    "some-schedule-id"
                )
            })
        }

    }
}

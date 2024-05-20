package com.urbanairship.iam.legacy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PreferenceDataStore
import com.urbanairship.TestClock
import com.urbanairship.automation.AutomationEngineInterface
import com.urbanairship.iam.analytics.InAppEventMessageID
import com.urbanairship.iam.analytics.InAppEventRecorderInterface
import com.urbanairship.json.jsonMapOf
import com.urbanairship.push.PushManager
import com.urbanairship.util.Clock
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
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
                        it.event.name == "in_app_resolution" &&
                        it.messageID == InAppEventMessageID.Legacy("some-schedule-id")
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

                it.event.data == expectedData && it.event.name == "in_app_resolution" && it.messageID == InAppEventMessageID.Legacy(
                    "some-schedule-id"
                )
            })
        }

    }
}

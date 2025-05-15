package com.urbanairship.iam.analytics.events

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.experiment.ExperimentResult
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
public class InAppResolutionEventTest {
    @Test
    public fun testButtonResolution() {
        val event = InAppResolutionEvent.buttonTap(
            identifier = "button id",
            description = "button description",
            displayTime = 100000.milliseconds
        )

        val expected = """
            {
               "resolution": {
                   "display_time":"100.00",
                   "button_description":"button description",
                   "type":"button_click",
                   "button_id":"button id"
                }
            }
        """.trimIndent()

        assertEquals("in_app_resolution", event.eventType.reportingName)
        assertEquals(JsonValue.parseString(expected), event.data?.toJsonValue())
    }

    @Test
    public fun testMessageTap() {
        val event = InAppResolutionEvent.messageTap(100_000.milliseconds)
        val expected = """
            {
               "resolution": {
                   "display_time":"100.00",
                   "type":"message_click"
                }
            }
        """.trimIndent()

        assertEquals("in_app_resolution", event.eventType.reportingName)
        assertEquals(JsonValue.parseString(expected), event.data?.toJsonValue())
    }

    @Test
    public fun testUserDismissed() {
        val event = InAppResolutionEvent.userDismissed(100_000.milliseconds)
        val expected = """
            {
               "resolution": {
                  "display_time":"100.00",
                  "type":"user_dismissed"
               }
            }
        """.trimIndent()

        assertEquals("in_app_resolution", event.eventType.reportingName)
        assertEquals(JsonValue.parseString(expected), event.data?.toJsonValue())
    }

    @Test
    public fun testTimedOut() {
        val event = InAppResolutionEvent.timedOut(100000.milliseconds)
        val expected = """
            {
               "resolution": {
                  "display_time": "100.00",
                  "type": "timed_out"
               }
            }
        """.trimIndent()

        assertEquals("in_app_resolution", event.eventType.reportingName)
        assertEquals(JsonValue.parseString(expected), event.data?.toJsonValue())
    }

    @Test
    public fun testControl() {
        val experiment = ExperimentResult(
            channelId = "channel id",
            contactId = "contact id",
            isMatching = true,
            allEvaluatedExperimentsMetadata = listOf(jsonMapOf("reporting" to "data"))
        )

        val event = InAppResolutionEvent.control(experiment)

        val expected = """
            {
               "resolution": {
                  "display_time":"0.00",
                  "type":"control"
               },
               "device": {
                  "channel_id": "channel id",
                  "contact_id": "contact id"
               }
            }
        """.trimIndent()

        assertEquals("in_app_resolution", event.eventType.reportingName)
        assertEquals(JsonValue.parseString(expected), event.data?.toJsonValue())
    }

    @Test
    public fun testAudienceExcludedEvent() {
        val event = InAppResolutionEvent.audienceExcluded()
        val expected = """
            {
            "resolution": {
               "display_time":"0.00",
               "type":"audience_check_excluded"
            }
         }
        """.trimIndent()

        assertEquals("in_app_resolution", event.eventType.reportingName)
        assertEquals(JsonValue.parseString(expected), event.data?.toJsonValue())
    }
}

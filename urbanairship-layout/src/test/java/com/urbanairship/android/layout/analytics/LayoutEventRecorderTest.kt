package com.urbanairship.android.layout.analytics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.analytics.Analytics
import com.urbanairship.analytics.ConversionData
import com.urbanairship.analytics.Event
import com.urbanairship.analytics.EventType
import com.urbanairship.experiment.ExperimentResult
import com.urbanairship.android.layout.analytics.events.LayoutEvent
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.meteredusage.AirshipMeteredUsage
import java.util.UUID
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class LayoutEventRecorderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val analytics: Analytics = mockk(relaxed = true)
    private val meteredUsage: AirshipMeteredUsage = mockk(relaxed = true)
    private val eventRecorder = LayoutEventRecorder(analytics, meteredUsage)
    private val reportingMetadata = JsonValue.wrap("reporting info")
    private val scheduleID = "5362C754-17A9-48B8-B101-60D9DC5688A2"
    private val campaigns = jsonMapOf("campaign1" to "data1", "campaign2" to "data2").toJsonValue()
    private val renderLocales = jsonMapOf("US" to "en-us").toJsonValue()
    private val experimentResults = ExperimentResult(
        channelId = "some channel",
        contactId = "some contact",
        isMatching = true,
        matchedExperimentId = null,
        allEvaluatedExperimentsMetadata = listOf(jsonMapOf("reporting" to "metadata"))
    )

    @Test
    public fun testEventData() {
        val data = LayoutEventData(
            event = defaultAppEvent,
            context = LayoutEventContext(
                reportingContext = reportingMetadata,
                experimentReportingData = experimentResults.allEvaluatedExperimentsMetadata
            ),
            source = LayoutEventSource.AIRSHIP,
            messageId = LayoutEventMessageId.AirshipId(scheduleID, campaigns),
            renderedLocale = renderLocales
        )

        val eventSlot = slot<Event>()
        justRun { analytics.addEvent(capture(eventSlot)) }

        eventRecorder.recordEvent(data)

        val recordedEvent = eventSlot.captured

        assertEquals(defaultAppEvent.eventType, recordedEvent.type)

        val json = """
            {
               "context":{
                  "reporting_context":"reporting info",
                  "experiments":[{
                     "reporting":"metadata"
                  }]
               },
               "source":"urban-airship",
               "rendered_locale":{
                  "US":"en-us"
               },
               "id":{
                  "campaigns":{
                     "campaign1":"data1",
                     "campaign2":"data2"
                  },
                  "message_id":"5362C754-17A9-48B8-B101-60D9DC5688A2"
               },
               "field":"something",
               "anotherField":"something something"
            }
        """.trimIndent()
        val expectedData = JsonValue.parseString(json)
        assertEquals(expectedData.toString(true), recordedEvent.getEventData(context, ConversionData()).toString(true))
    }

    @Test
    public fun testConversionIDs() {
        val conversionData = ConversionData(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString()
        )
        val data = LayoutEventData(
            event = defaultAppEvent,
            context = LayoutEventContext(
                reportingContext = reportingMetadata,
                experimentReportingData = experimentResults.allEvaluatedExperimentsMetadata
            ),
            source = LayoutEventSource.AIRSHIP,
            messageId = LayoutEventMessageId.AirshipId(scheduleID, campaigns),
            renderedLocale = renderLocales
        )

        val eventSlot = slot<Event>()
        justRun { analytics.addEvent(capture(eventSlot)) }

        eventRecorder.recordEvent(data)

        val recordedEvent = eventSlot.captured

        assertEquals(defaultAppEvent.eventType, recordedEvent.type)

        val json = """
            {
               "conversion_send_id":"${conversionData.conversionSendId}",
               "conversion_metadata":"${conversionData.conversionMetadata}",
               "context":{
                  "reporting_context":"reporting info",
                  "experiments":[{
                     "reporting":"metadata"
                  }]
               },
               "source":"urban-airship",
               "rendered_locale":{
                  "US":"en-us"
               },
               "id":{
                  "campaigns":{
                     "campaign1":"data1",
                     "campaign2":"data2"
                  },
                  "message_id":"5362C754-17A9-48B8-B101-60D9DC5688A2"
               },
               "field":"something",
               "anotherField":"something something"
            }
        """.trimIndent()
        val expectedData = JsonValue.parseString(json)
        assertEquals(expectedData.toString(true), recordedEvent.getEventData(context, conversionData).toString(true))
    }

    @Test
    public fun testEventDataError() {
        val data = LayoutEventData(
            event = errorAppEvent,
            context = LayoutEventContext(
                reportingContext = reportingMetadata,
                experimentReportingData = experimentResults.allEvaluatedExperimentsMetadata
            ),
            source = LayoutEventSource.AIRSHIP,
            messageId = LayoutEventMessageId.AirshipId(scheduleID, campaigns),
            renderedLocale = renderLocales
        )

        eventRecorder.recordEvent(data)
        verify(exactly = 0) { analytics.addEvent(any()) }
    }

    private var defaultAppEvent = object : LayoutEvent {
        override val eventType: EventType = EventType.IN_APP_DISPLAY
        override val data: JsonSerializable = jsonMapOf(
            "field" to "something",
            "anotherField" to "something something"
        )
    }

    private var errorAppEvent = object : LayoutEvent {
        override val eventType: EventType = EventType.IN_APP_DISPLAY
        override val data: JsonSerializable
            get() { throw JsonException("test") }
    }
}

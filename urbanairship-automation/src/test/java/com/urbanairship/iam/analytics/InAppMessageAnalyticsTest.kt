package com.urbanairship.iam.analytics

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.android.layout.reporting.FormInfo
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.analytics.events.InAppEvent
import com.urbanairship.iam.content.Custom
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.experiment.ExperimentResult
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.meteredusage.AirshipMeteredUsage
import com.urbanairship.meteredusage.MeteredUsageEventEntity
import com.urbanairship.meteredusage.MeteredUsageType
import java.util.UUID
import io.mockk.coEvery
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InAppMessageAnalyticsTest {
    private val campaigns = jsonMapOf("campaign1" to "data1", "campaign2" to "data2").toJsonValue()
    private val experimentResult = ExperimentResult(
        channelId = "channel id",
        contactId = "contact id",
        isMatching = true,
        allEvaluatedExperimentsMetadata = listOf(jsonMapOf("reporting" to "metadata"))
    )
    private val scheduleId = UUID.randomUUID().toString()
    private val reportingMetadata = JsonValue.wrap("reporting info")
    private val eventRecorder: InAppEventRecorderInterface = mockk()
    private val impressionRecorder: AirshipMeteredUsage = mockk()
    private val clock = TestClock()
    private var event: InAppEventData? = null

    @Before
    public fun setup() {
        coEvery { eventRecorder.recordEvent(any()) } answers {
            event = firstArg()
        }
    }

    @Test
    public fun testSource(): TestResult = runTest {
        val analytics = makeAnalytics()
        analytics.recordEvent(TestInAppEvent(), layoutContext = null)

        assertEquals(event?.messageId, InAppEventMessageId.AirshipId(scheduleId, campaigns))
        assertEquals(event?.source, InAppEventSource.AIRSHIP)
    }

    @Test
    public fun testAppDefined(): TestResult = runTest {
        val analytics = makeAnalytics(source =  InAppMessage.Source.APP_DEFINED)
        analytics.recordEvent(TestInAppEvent(), layoutContext = null)

        assertEquals(event?.messageId, InAppEventMessageId.AppDefined(scheduleId))
        assertEquals(event?.source, InAppEventSource.APP_DEFINED)
    }

    @Test
    public fun testLegacyMessageId(): TestResult = runTest {
        val analytics = makeAnalytics(source =  InAppMessage.Source.LEGACY_PUSH)
        analytics.recordEvent(TestInAppEvent(), layoutContext = null)

        assertEquals(event?.messageId, InAppEventMessageId.Legacy(scheduleId))
        assertEquals(event?.source, InAppEventSource.AIRSHIP)
    }

    @Test
    public fun testData(): TestResult = runTest {
        val layoutData = LayoutData(
            FormInfo("form-id", "form-type", "response-type", true),
            PagerData("pager-id", 1, "page-id", 2, false),
            "button-id"
        )

        val expectedContext = InAppEventContext.makeContext(
            reportingContext = reportingMetadata,
            experimentResult = experimentResult,
            layoutContext = layoutData
        )

        makeAnalytics(source =  InAppMessage.Source.LEGACY_PUSH)
            .recordEvent(TestInAppEvent(), layoutContext = layoutData)

        assertEquals(event?.context, expectedContext)
        assertEquals(event?.renderedLocale, jsonMapOf("US" to "en-US").toJsonValue())
        assertEquals(event?.event?.name, "test_event")
    }

    @Test
    public fun testImpression(): TestResult = runTest {
        val productId = "test-product-id"
        val contactId = "test-contact-id"

        clock.currentTimeMillis = 0

        var impression: MeteredUsageEventEntity? = null
        coEvery { impressionRecorder.addEvent(any()) } answers { impression = firstArg() }

        makeAnalytics(
            source =  InAppMessage.Source.LEGACY_PUSH,
            productId = productId,
            contactId = contactId)
            .recordImpression()

        assertEquals(scheduleId, impression?.entityId)
        assertEquals(MeteredUsageType.IN_APP_EXPERIENCE_IMPRESSION, impression?.type)
        assertEquals(productId, impression?.product)
        assertEquals(reportingMetadata, impression?.reportingContext)
        assertEquals(0L, impression?.timestamp)
        assertEquals(contactId, impression?.contactId)
    }

    @Test
    public fun testReportingDisabled(): TestResult = runTest {
        var impression: MeteredUsageEventEntity? = null
        coEvery { impressionRecorder.addEvent(any()) } answers { impression = firstArg() }

        val analytics = makeAnalytics(
            source =  InAppMessage.Source.LEGACY_PUSH,
            isReportingEnabled = false
        )

        analytics.recordEvent(TestInAppEvent(), null)
        analytics.recordImpression()

        assertNull(event)
        assertNull(impression)
    }

    private fun makeAnalytics(
        source:  InAppMessage.Source =  InAppMessage.Source.REMOTE_DATA,
        productId: String? = null,
        contactId: String? = null,
        isReportingEnabled: Boolean? = null
    ): InAppMessageAnalytics {
        return InAppMessageAnalytics(
            scheduleId = scheduleId,
            productId = productId,
            contactId = contactId,
            message = InAppMessage(
                name = "name",
                displayContent = InAppMessageDisplayContent.CustomContent(Custom(JsonValue.wrap("custom"))),
                source = source,
                renderedLocale = jsonMapOf("US" to "en-US").toJsonValue(),
                isReportingEnabled = isReportingEnabled
            ),
            campaigns = campaigns,
            experimentResult = experimentResult,
            reportingMetadata = reportingMetadata,
            eventRecorder = eventRecorder,
            impressionRecorder = impressionRecorder,
            clock = clock
        )
    }
}

private class TestInAppEvent(
    override val name: String = "test_event",
    override val data: JsonSerializable? = null
) : InAppEvent

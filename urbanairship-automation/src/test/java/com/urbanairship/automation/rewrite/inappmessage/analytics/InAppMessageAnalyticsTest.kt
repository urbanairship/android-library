package com.urbanairship.automation.rewrite.inappmessage.analytics

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.android.layout.reporting.FormInfo
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppEvent
import com.urbanairship.automation.rewrite.inappmessage.content.Custom
import com.urbanairship.automation.rewrite.inappmessage.content.InAppMessageDisplayContent
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
    private val scheduleID = UUID.randomUUID().toString()
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

        assertEquals(event?.messageID, InAppEventMessageID.AirshipID(scheduleID, campaigns))
        assertEquals(event?.source, InAppEventSource.AIRSHIP)
    }

    @Test
    public fun testAppDefined(): TestResult = runTest {
        val analytics = makeAnalytics(source = InAppMessage.InAppMessageSource.APP_DEFINED)
        analytics.recordEvent(TestInAppEvent(), layoutContext = null)

        assertEquals(event?.messageID, InAppEventMessageID.AppDefined(scheduleID))
        assertEquals(event?.source, InAppEventSource.APP_DEFINED)
    }

    @Test
    public fun testLegacyMessageID(): TestResult = runTest {
        val analytics = makeAnalytics(source = InAppMessage.InAppMessageSource.LEGACY_PUSH)
        analytics.recordEvent(TestInAppEvent(), layoutContext = null)

        assertEquals(event?.messageID, InAppEventMessageID.Legacy(scheduleID))
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

        makeAnalytics(source = InAppMessage.InAppMessageSource.LEGACY_PUSH)
            .recordEvent(TestInAppEvent(), layoutContext = layoutData)

        assertEquals(event?.context, expectedContext)
        assertEquals(event?.renderedLocale, mapOf("US" to JsonValue.wrap("en-US")))
        assertEquals(event?.event?.name, "test_event")
    }

    @Test
    public fun testImpression(): TestResult = runTest {
        val productID = "test-product-id"
        val contactID = "test-contact-id"

        clock.currentTimeMillis = 0

        var impression: MeteredUsageEventEntity? = null
        coEvery { impressionRecorder.addEvent(any()) } answers { impression = firstArg() }

        makeAnalytics(
            source = InAppMessage.InAppMessageSource.LEGACY_PUSH,
            productID = productID,
            contactID = contactID)
            .recordImpression()

        assertEquals(scheduleID, impression?.entityId)
        assertEquals(MeteredUsageType.IN_APP_EXPERIENCE_IMPRESSION, impression?.type)
        assertEquals(productID, impression?.product)
        assertEquals(reportingMetadata, impression?.reportingContext)
        assertEquals(0L, impression?.timestamp)
        assertEquals(contactID, impression?.contactId)
    }

    @Test
    public fun testReportingDisabled(): TestResult = runTest {
        var impression: MeteredUsageEventEntity? = null
        coEvery { impressionRecorder.addEvent(any()) } answers { impression = firstArg() }

        val analytics = makeAnalytics(
            source = InAppMessage.InAppMessageSource.LEGACY_PUSH,
            isReportingEnabled = false
        )

        analytics.recordEvent(TestInAppEvent(), null)
        analytics.recordImpression()

        assertNull(event)
        assertNull(impression)
    }

    private fun makeAnalytics(
        source: InAppMessage.InAppMessageSource = InAppMessage.InAppMessageSource.REMOTE_DATA,
        productID: String? = null,
        contactID: String? = null,
        isReportingEnabled: Boolean? = null
    ): InAppMessageAnalytics {
        return InAppMessageAnalytics(
            scheduleID = scheduleID,
            productID = productID,
            contactID = contactID,
            message = InAppMessage(
                name = "name",
                displayContent = InAppMessageDisplayContent.CustomContent(Custom(JsonValue.wrap("custom"))),
                source = source,
                renderedLocale = mapOf("US" to JsonValue.wrap("en-US")),
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

package com.urbanairship.iam.analytics

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.analytics.EventType
import com.urbanairship.android.layout.reporting.FormInfo
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.automation.engine.PreparedScheduleInfo
import com.urbanairship.experiment.ExperimentResult
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.analytics.events.InAppDisplayEvent
import com.urbanairship.iam.analytics.events.InAppEvent
import com.urbanairship.iam.content.Custom
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.meteredusage.MeteredUsageEventEntity
import com.urbanairship.meteredusage.MeteredUsageType
import java.util.UUID
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InAppMessageAnalyticsTest {
    private val eventRecorder: InAppEventRecorderInterface = mockk(relaxed = true)
    private val historyStore: MessageDisplayHistoryStore = mockk()
    private val preparedInfo = PreparedScheduleInfo(
        scheduleId = UUID.randomUUID().toString(),
        productId = UUID.randomUUID().toString(),
        campaigns = JsonValue.wrap(UUID.randomUUID().toString()),
        contactId = UUID.randomUUID().toString(),
        experimentResult = ExperimentResult(
            channelId = UUID.randomUUID().toString(),
            contactId = UUID.randomUUID().toString(),
            isMatching = true,
            allEvaluatedExperimentsMetadata = listOf(
                jsonMapOf("key" to UUID.randomUUID().toString())
            )
        ),
        reportingContext = JsonValue.wrap(UUID.randomUUID().toString()),
        triggerSessionId = UUID.randomUUID().toString()
    )

    private val testDispatcher = StandardTestDispatcher()
    private val clock = TestClock()
    private var event: InAppEventData? = null
    private var displayHistory: MessageDisplayHistory? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)

        coEvery { eventRecorder.recordEvent(any()) } answers {
            event = firstArg()
        }
        coEvery { historyStore.set(any(), any()) } answers {
            displayHistory = firstArg()
        }
        coEvery { historyStore.get(any()) } answers {
            displayHistory ?: MessageDisplayHistory()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testSource(): TestResult = runTest {
        val analytics = makeAnalytics()
        analytics.recordEvent(TestInAppEvent(), layoutContext = null)

        assertEquals(event?.messageId, InAppEventMessageId.AirshipId(preparedInfo.scheduleId, preparedInfo.campaigns))
        assertEquals(event?.source, InAppEventSource.AIRSHIP)
    }

    @Test
    public fun testAppDefined(): TestResult = runTest {
        val analytics = makeAnalytics(source = InAppMessage.Source.APP_DEFINED)
        analytics.recordEvent(TestInAppEvent(), layoutContext = null)

        assertEquals(event?.messageId, InAppEventMessageId.AppDefined(preparedInfo.scheduleId))
        assertEquals(event?.source, InAppEventSource.APP_DEFINED)
    }

    @Test
    public fun testLegacyMessageId(): TestResult = runTest {
        val analytics = makeAnalytics(source = InAppMessage.Source.LEGACY_PUSH)
        analytics.recordEvent(TestInAppEvent(), layoutContext = null)

        assertEquals(event?.messageId, InAppEventMessageId.Legacy(preparedInfo.scheduleId))
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
            reportingContext = preparedInfo.reportingContext,
            experimentResult = preparedInfo.experimentResult,
            layoutContext = layoutData,
            displayContext = InAppEventContext.Display(
                triggerSessionId = preparedInfo.triggerSessionId,
                isFirstDisplay = true,
                isFirstDisplayTriggerSessionId = true
            )
        )

        makeAnalytics(source = InAppMessage.Source.LEGACY_PUSH)
            .recordEvent(TestInAppEvent(), layoutContext = layoutData)

        assertEquals(event?.context, expectedContext)
        assertEquals(event?.renderedLocale, jsonMapOf("US" to "en-US").toJsonValue())
        assertEquals(event?.event?.eventType, EventType.IN_APP_DISPLAY)
    }

    @Test
    public fun testSingleImpression(): TestResult = runTest {
        clock.currentTimeMillis = 0

        val analytics = makeAnalytics(source = InAppMessage.Source.LEGACY_PUSH)

        analytics.recordEvent(InAppDisplayEvent(), null)
        // This second run should not trigger a recordImpressionEvent
        analytics.recordEvent(InAppDisplayEvent(), null)

        verify (exactly = 1) { eventRecorder.recordImpressionEvent(withArg {
            assertEquals(preparedInfo.scheduleId, it.entityId)
            assertEquals(MeteredUsageType.IN_APP_EXPERIENCE_IMPRESSION, it.type)
            assertEquals(preparedInfo.productId, it.product)
            assertEquals(preparedInfo.reportingContext, it.reportingContext)
            assertEquals(0L, it.timestamp)
            assertEquals(preparedInfo.contactId, it.contactId)
        }) }

        val displayHistory = historyStore.get(preparedInfo.scheduleId)
        assertEquals(clock.currentTimeMillis, displayHistory.lastImpression?.date)
        assertEquals(preparedInfo.triggerSessionId, displayHistory.lastImpression?.triggerSessionId)
    }

    @Test
    public fun testImpressionInterval(): TestResult = runTest {
        clock.currentTimeMillis = 0

        val analytics = makeAnalytics(
            source = InAppMessage.Source.LEGACY_PUSH,
            displayImpressionRule = InAppDisplayImpressionRule.Interval(10)
        )

        analytics.recordEvent(InAppDisplayEvent(), null)

        verify (exactly = 1) { eventRecorder.recordImpressionEvent(withArg {
            assertEquals(preparedInfo.scheduleId, it.entityId)
            assertEquals(MeteredUsageType.IN_APP_EXPERIENCE_IMPRESSION, it.type)
            assertEquals(preparedInfo.productId, it.product)
            assertEquals(preparedInfo.reportingContext, it.reportingContext)
            assertEquals(0L, it.timestamp)
            assertEquals(preparedInfo.contactId, it.contactId)
        }) }

        // This second run should not trigger a recordImpressionEvent
        analytics.recordEvent(InAppDisplayEvent(), null)

        var displayHistory = historyStore.get(preparedInfo.scheduleId)
        assertEquals(0L, displayHistory.lastImpression?.date)
        assertEquals(preparedInfo.triggerSessionId, displayHistory.lastImpression?.triggerSessionId)

        clock.currentTimeMillis += 9L
        // This third run should still not trigger a recordImpressionEvent
        analytics.recordEvent(InAppDisplayEvent(), null)

        clock.currentTimeMillis += 1L
        analytics.recordEvent(InAppDisplayEvent(), null)

        verify (exactly = 1) { eventRecorder.recordImpressionEvent(withArg {
            assertEquals(preparedInfo.scheduleId, it.entityId)
            assertEquals(MeteredUsageType.IN_APP_EXPERIENCE_IMPRESSION, it.type)
            assertEquals(preparedInfo.productId, it.product)
            assertEquals(preparedInfo.reportingContext, it.reportingContext)
            assertEquals(10L, it.timestamp)
            assertEquals(preparedInfo.contactId, it.contactId)
        }) }

        displayHistory = historyStore.get(preparedInfo.scheduleId)
        assertEquals(clock.currentTimeMillis, displayHistory.lastImpression?.date)
        assertEquals(preparedInfo.triggerSessionId, displayHistory.lastImpression?.triggerSessionId)
    }

    @Test
    public fun testReportingDisabled(): TestResult = runTest {
        var impression: MeteredUsageEventEntity? = null
        coEvery { eventRecorder.recordImpressionEvent(any()) } answers { impression = firstArg() }

        val analytics = makeAnalytics(
            source =  InAppMessage.Source.LEGACY_PUSH,
            isReportingEnabled = false
        )

        analytics.recordEvent(InAppDisplayEvent(), null)

        assertNull(event)
        // impressions are still recorded
        assertNotNull(impression)
    }

    @Test
    public fun testDisplayUpdatesHistory(): TestResult = runTest {
        val analytics = makeAnalytics(
            source = InAppMessage.Source.LEGACY_PUSH,
            isReportingEnabled = true
        )

        var displayHistory = historyStore.get(preparedInfo.scheduleId)
        assertNull(displayHistory.lastDisplay?.triggerSessionId)

        displayHistory = historyStore.get(preparedInfo.scheduleId)
        analytics.recordEvent(InAppDisplayEvent(), null)
    }

    @Test
    public fun testDisplayContextNewIAA(): TestResult = runTest {
        val analytics = makeAnalytics(
            source = InAppMessage.Source.LEGACY_PUSH,
            isReportingEnabled = true
        )

        val firstDisplayContext = InAppEventContext.Display(
            triggerSessionId = preparedInfo.triggerSessionId,
            isFirstDisplay = true,
            isFirstDisplayTriggerSessionId = true
        )

        val secondDisplayContext = InAppEventContext.Display(
            triggerSessionId = preparedInfo.triggerSessionId,
            isFirstDisplay = false,
            isFirstDisplayTriggerSessionId = false
        )

        analytics.recordEvent(TestInAppEvent(), null)
        // event before a display
        verify { eventRecorder.recordEvent(withArg { assertEquals(firstDisplayContext, it.context?.display) }) }
        analytics.recordEvent(InAppDisplayEvent(), null)
        // first display
        verify { eventRecorder.recordEvent(withArg { assertEquals(firstDisplayContext, it.context?.display) }) }
        analytics.recordEvent(TestInAppEvent(), null)
        // event after display
        verify { eventRecorder.recordEvent(withArg { assertEquals(firstDisplayContext, it.context?.display) }) }
        analytics.recordEvent(InAppDisplayEvent(), null)
        // second display
        verify { eventRecorder.recordEvent(withArg { assertEquals(secondDisplayContext, it.context?.display) }) }
        analytics.recordEvent(TestInAppEvent(), null)
        // event after display
        verify { eventRecorder.recordEvent(withArg { assertEquals(secondDisplayContext, it.context?.display) }) }
    }

    @Test
    public fun testDisplayContextPreviouslyDisplayIAX(): TestResult = runTest {
        val analytics = makeAnalytics(
            source = InAppMessage.Source.LEGACY_PUSH,
            isReportingEnabled = true,
            displayHistory = MessageDisplayHistory(
                lastDisplay = MessageDisplayHistory.LastDisplay(UUID.randomUUID().toString())
            )
        )

        val firstDisplayContext = InAppEventContext.Display(
            triggerSessionId = preparedInfo.triggerSessionId,
            isFirstDisplay = false,
            isFirstDisplayTriggerSessionId = true
        )

        val secondDisplayContext = InAppEventContext.Display(
            triggerSessionId = preparedInfo.triggerSessionId,
            isFirstDisplay = false,
            isFirstDisplayTriggerSessionId = false
        )

        analytics.recordEvent(TestInAppEvent(), null)
        // event before a display
        verify { eventRecorder.recordEvent(withArg { assertEquals(firstDisplayContext, it.context?.display) }) }
        analytics.recordEvent(InAppDisplayEvent(), null)
        // first display
        verify { eventRecorder.recordEvent(withArg { assertEquals(firstDisplayContext, it.context?.display) }) }
        analytics.recordEvent(TestInAppEvent(), null)
        // event after display
        verify { eventRecorder.recordEvent(withArg { assertEquals(firstDisplayContext, it.context?.display) }) }
        analytics.recordEvent(InAppDisplayEvent(), null)
        // second display
        verify { eventRecorder.recordEvent(withArg { assertEquals(secondDisplayContext, it.context?.display) }) }
        analytics.recordEvent(TestInAppEvent(), null)
        // event after display
        verify { eventRecorder.recordEvent(withArg { assertEquals(secondDisplayContext, it.context?.display) }) }
    }

    @Test
    public fun testDisplayContextSameTriggerSessionId(): TestResult = runTest {
        val analytics = makeAnalytics(
            source = InAppMessage.Source.LEGACY_PUSH,
            isReportingEnabled = true,
            displayHistory = MessageDisplayHistory(
                lastDisplay = MessageDisplayHistory.LastDisplay(preparedInfo.triggerSessionId)
            )
        )

        val displayContexts = mutableListOf<InAppEventContext.Display?>()
        coEvery { eventRecorder.recordEvent(any()) } answers {
            displayContexts.add((firstArg() as InAppEventData).context?.display)
        }

        analytics.recordEvent(TestInAppEvent(), null)
        analytics.recordEvent(InAppDisplayEvent(), null)
        analytics.recordEvent(TestInAppEvent(), null)
        analytics.recordEvent(InAppDisplayEvent(), null)
        analytics.recordEvent(TestInAppEvent(), null)

        val firstDisplayContext = InAppEventContext.Display(
            triggerSessionId = preparedInfo.triggerSessionId,
            isFirstDisplay = false,
            isFirstDisplayTriggerSessionId = false
        )

        val secondDisplayContext = InAppEventContext.Display(
            triggerSessionId = preparedInfo.triggerSessionId,
            isFirstDisplay = false,
            isFirstDisplayTriggerSessionId = false
        )

        val expected = listOf<InAppEventContext.Display?>(
            // event before a display
            firstDisplayContext,
            // first display
            firstDisplayContext,
            // event after display
            firstDisplayContext,
            // second display
            secondDisplayContext,
            // event after display
            secondDisplayContext
        )

        assertEquals(expected, displayContexts.toList())
    }

    private fun makeAnalytics(
        source: InAppMessage.Source = InAppMessage.Source.REMOTE_DATA,
        isReportingEnabled: Boolean? = null,
        displayImpressionRule: InAppDisplayImpressionRule = InAppDisplayImpressionRule.Once,
        displayHistory: MessageDisplayHistory = MessageDisplayHistory()
    ): InAppMessageAnalytics {
        return InAppMessageAnalytics(
            preparedScheduleInfo = preparedInfo,
            message = InAppMessage(
                name = "name",
                displayContent = InAppMessageDisplayContent.CustomContent(Custom(JsonValue.wrap("custom"))),
                source = source,
                renderedLocale = jsonMapOf("US" to "en-US").toJsonValue(),
                isReportingEnabled = isReportingEnabled
            ),
            eventRecorder = eventRecorder,
            displayImpressionRule = displayImpressionRule,
            historyStore = historyStore,
            displayHistory = displayHistory,
            clock = clock
        )
    }
}

private class TestInAppEvent(
    override val eventType: EventType = EventType.IN_APP_DISPLAY,
    override val data: JsonSerializable? = null
) : InAppEvent

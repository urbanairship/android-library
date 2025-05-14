package com.urbanairship.iam.adapter

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.event.ReportingEvent.FormResult
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.android.layout.reporting.ThomasFormField
import com.urbanairship.automation.utils.ManualActiveTimer
import com.urbanairship.iam.adapter.layout.LayoutListener
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.iam.analytics.events.InAppButtonTapEvent
import com.urbanairship.iam.analytics.events.InAppDisplayEvent
import com.urbanairship.iam.analytics.events.InAppEvent
import com.urbanairship.iam.analytics.events.InAppFormDisplayEvent
import com.urbanairship.iam.analytics.events.InAppFormResultEvent
import com.urbanairship.iam.analytics.events.InAppGestureEvent
import com.urbanairship.iam.analytics.events.InAppPageActionEvent
import com.urbanairship.iam.analytics.events.InAppPageSwipeEvent
import com.urbanairship.iam.analytics.events.InAppPageViewEvent
import com.urbanairship.iam.analytics.events.InAppPagerSummaryEvent
import com.urbanairship.iam.analytics.events.InAppResolutionEvent
import com.urbanairship.json.JsonValue
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class LayoutAdapterListenerTest {
    private val analytics: InAppMessageAnalyticsInterface = mockk()
    private var recordedEvents = mutableListOf<Pair<InAppEvent, LayoutData?>>()
    private val clock = TestClock()
    private val timer = ManualActiveTimer(clock)
    private var displayResult: DisplayResult? = null
    private val listener = LayoutListener(analytics, timer = timer) { displayResult = it }
    private val defaultLayoutData = LayoutData(null, null, "button")

    @Before
    public fun setup() {
        every { analytics.recordEvent(any(), any()) } answers {
            recordedEvents.add(Pair(firstArg(), secondArg()))
        }
    }

    @Test
    public fun testFormSubmitted() {
        clock.currentTimeMillis = 0
        timer.start()
        assertTrue(timer.isStarted)

        val form = FormResult(
            data = ReportingEvent.FormResultData(
                ThomasFormField.Form(
                    identifier = "form id",
                    responseType = null,
                    children = emptySet(),
                    fieldType = ThomasFormField.FieldType.just(emptySet())
                )
            ),
            context = defaultLayoutData,
            mapOf(),
            emptyList()
        )

        listener.onReportingEvent(form)

        verifyEvents(listOf(
            Pair(InAppFormResultEvent(form.data), defaultLayoutData))
        )

        assertTrue(timer.isStarted)
        assertNull(displayResult)
    }

    @Test
    public fun testFormDisplayed() {
        clock.currentTimeMillis = 0
        timer.start()

        val event = ReportingEvent.FormDisplay(
            data = ReportingEvent.FormDisplayData(
                identifier = "form id",
                formType = "some type",
                responseType = "some response type"
            ),
            context = defaultLayoutData
        )

        listener.onReportingEvent(event)

        verifyEvents(listOf(
            Pair(InAppFormDisplayEvent(event.data), defaultLayoutData)
        ))

        assertTrue(timer.isStarted)
        assertNull(displayResult)
    }

    @Test
    public fun testButtonTap() {
        timer.start()

        val data = ReportingEvent.ButtonTapData(
            identifier = "button id",
            reportingMetadata = JsonValue.wrap("some metadata")
        )

        listener.onReportingEvent(
            event = ReportingEvent.ButtonTap(
                data = data,
                context = defaultLayoutData
            )
        )

        verifyEvents(listOf(
            Pair(InAppButtonTapEvent(data), defaultLayoutData))
        )

        assertTrue(timer.isStarted)
        assertNull(displayResult)
    }

    @Test
    public fun testDismissed() {
        clock.currentTimeMillis = 0
        timer.start()
        clock.currentTimeMillis = 10


        listener.onDismiss(false)
        verifyEvents(listOf(
            Pair(InAppResolutionEvent.userDismissed(5), null)
        ))

        assertFalse(timer.isStarted)
        assertEquals(displayResult, DisplayResult.FINISHED)
    }

    @Test
    public fun testButtonDismiss() {
        clock.currentTimeMillis = 0
        timer.start()
        clock.currentTimeMillis = 10

        listener.onReportingEvent(
            event = ReportingEvent.Dismiss(
                data = ReportingEvent.DismissData.ButtonTapped(
                    identifier = "button id",
                    description = "button description",
                    cancel = false
                ),
                displayTime = 10.milliseconds,
                context = defaultLayoutData
            )
        )

        verifyEvents(listOf(
            Pair(
                InAppResolutionEvent.buttonTap(
                    identifier = "button id",
                    description = "button description",
                    displayTime = 10
                ),
                defaultLayoutData
            )
        ))

        assertFalse(timer.isStarted)
        assertEquals(displayResult, DisplayResult.FINISHED)
    }

    @Test
    public fun testButtonCancel() {
        clock.currentTimeMillis = 0
        timer.start()
        clock.currentTimeMillis = 10

        listener.onReportingEvent(
            event = ReportingEvent.Dismiss(
                data = ReportingEvent.DismissData.ButtonTapped(
                    identifier = "button id",
                    description = "button description",
                    cancel = true
                ),
                displayTime = 5.milliseconds,
                context = defaultLayoutData
            )
        )

        verifyEvents(listOf(
            Pair(
                InAppResolutionEvent.buttonTap(
                    identifier = "button id",
                    description = "button description",
                    displayTime = 10
                ),
                defaultLayoutData
            )
        ))

        assertFalse(timer.isStarted)
        assertEquals(displayResult, DisplayResult.CANCEL)
    }

    @Test
    public fun testPageView() {
        timer.start()

        val pageInfo = makePagerInfo("foo", 0)
        val data = ReportingEvent.PageViewData(
            identifier = pageInfo.identifier,
            pageIdentifier = pageInfo.pageId,
            pageIndex = pageInfo.index,
            pageViewCount = 1,
            pageCount = pageInfo.count,
            completed = pageInfo.isCompleted
        )

        listener.onReportingEvent(
            event = ReportingEvent.PageView(
                data = data,
                context = defaultLayoutData
            )
        )

        verifyEvents(listOf(
            Pair(InAppPageViewEvent(data), defaultLayoutData))
        )

        assertTrue(timer.isStarted)
        assertNull(displayResult)
    }

    @Test
    public fun testPageGesture() {
        timer.start()

        val data = ReportingEvent.GestureData(
            identifier = "gesture id",
            reportingMetadata = JsonValue.wrap("some metadata")
        )

        listener.onReportingEvent(
            event = ReportingEvent.Gesture(
                data = data,
                context = defaultLayoutData
            )
        )

        verifyEvents(listOf(
            Pair(InAppGestureEvent(data), defaultLayoutData))
        )

        assertTrue(timer.isStarted)
        assertNull(displayResult)
    }

    @Test
    public fun testPageAction() {
        timer.start()

        val data = ReportingEvent.PageActionData(
            identifier = "action id", metadata = JsonValue.wrap("some metadata")
        )

        listener.onReportingEvent(
            event = ReportingEvent.PageAction(
                data = data,
                context = defaultLayoutData
            )
        )

        verifyEvents(listOf(
            Pair(InAppPageActionEvent(data), defaultLayoutData))
        )

        assertTrue(timer.isStarted)
        assertNull(displayResult)
    }

    @Test
    public fun testPageSwipe() {
        timer.start()
        val pager = makePagerInfo("foo", 0)
        val data = ReportingEvent.PageSwipeData(
            identifier = pager.identifier,
            toPageIndex = 1,
            toPageIdentifier = "page-1",
            fromPageIndex = 0,
            fromPageIdentifier = "page-0"
        )

        listener.onReportingEvent(
            event = ReportingEvent.PageSwipe(
                data = data,
                context = defaultLayoutData
            )
        )

        verifyEvents(listOf(
            Pair(InAppPageSwipeEvent(data), defaultLayoutData))
        )

        assertTrue(timer.isStarted)
        assertNull(displayResult)
    }

    @Test
    public fun testDismissPagerSummary() {
        clock.currentTimeMillis = 0
        timer.start()
        val page0 = makePagerInfo("foo", 0)
        val page1 = makePagerInfo("foo", 1)

        val actions = listOf(
            ReportingEvent.PageView(
                data = ReportingEvent.PageViewData(
                    identifier = page0.identifier,
                    pageIdentifier = page0.pageId,
                    pageIndex = page0.index,
                    pageViewCount = 5,
                    pageCount = page0.count,
                    completed = page0.isCompleted
                ),
                context = defaultLayoutData
            ),
            ReportingEvent.PageView(
                data = ReportingEvent.PageViewData(
                    identifier = page1.identifier,
                    pageIdentifier = page1.pageId,
                    pageIndex = page1.index,
                    pageViewCount = 10,
                    pageCount = page1.count,
                    completed = page1.isCompleted
                ),
                context = defaultLayoutData
            ),
            ReportingEvent.PageView(
                data = ReportingEvent.PageViewData(
                    identifier = page0.identifier,
                    pageIdentifier = page0.pageId,
                    pageIndex = page0.index,
                    pageViewCount = 20,
                    pageCount = page0.count,
                    completed = page0.isCompleted
                ),
                context = defaultLayoutData
            )
        )

        actions.forEachIndexed { index, event ->
            listener.onReportingEvent(event)
            clock.currentTimeMillis += 10 * max(index, 1)
        }

        val dismiss = ReportingEvent.Dismiss(
            data = ReportingEvent.DismissData.UserDismissed,
            displayTime = 40.milliseconds,
            context = defaultLayoutData
        )
        listener.onReportingEvent(dismiss)

        val expected = listOf(
            Pair(InAppPageViewEvent(actions[0].data), defaultLayoutData),
            Pair(InAppPageViewEvent(actions[1].data), defaultLayoutData),
            Pair(InAppPageViewEvent(actions[2].data), defaultLayoutData),
            Pair(InAppResolutionEvent.userDismissed(30), defaultLayoutData),
            Pair(
                InAppPagerSummaryEvent(
                    eventData = ReportingEvent.PageSummaryData(
                        identifier = page0.identifier,
                        pageCount = page0.count,
                        completed = page0.isCompleted,
                        viewedPages = listOf(
                            ReportingEvent.PageSummaryData.PageView(
                                identifier = page0.pageId,
                                index = 0,
                                displayTime = 5.milliseconds
                            ),
                            ReportingEvent.PageSummaryData.PageView(
                                identifier = page1.pageId,
                                index = 1,
                                displayTime = 10.milliseconds
                            ),
                            ReportingEvent.PageSummaryData.PageView(
                                identifier = page0.pageId,
                                index = 0,
                                displayTime = 20.milliseconds
                            )
                        )
                    )
                ),
                defaultLayoutData
            )
        )
        verifyEvents(expected)

        assertFalse(timer.isStarted)
        assertEquals(displayResult, DisplayResult.FINISHED)
    }

    @Test
    public fun testVisibilityChanged() {
        timer.start()

        listener.onVisibilityChanged(isVisible = true, isForegrounded = true)
        verifyEvents(listOf(
            Pair(InAppDisplayEvent(), null)
        ))
        assertTrue(timer.isStarted)

        listener.onVisibilityChanged(isVisible = false, isForegrounded = true)
        verifyEvents(listOf(
            Pair(InAppDisplayEvent(), null)
        ))
        assertFalse(timer.isStarted)

        listener.onVisibilityChanged(isVisible = false, isForegrounded = false)
        verifyEvents(listOf(
            Pair(InAppDisplayEvent(), null)
        ))
        assertFalse(timer.isStarted)

        listener.onVisibilityChanged(isVisible = true, isForegrounded = false)
        verifyEvents(listOf(
            Pair(InAppDisplayEvent(), null)
        ))
        assertFalse(timer.isStarted)

        listener.onVisibilityChanged(isVisible = true, isForegrounded = true)
        verifyEvents(listOf(
            Pair(InAppDisplayEvent(), null),
            Pair(InAppDisplayEvent(), null)
        ))
        assertTrue(timer.isStarted)

        assertNull(displayResult)
    }

    @Test
    public fun testTimedOut() {
        clock.currentTimeMillis = 0
        timer.start()
        clock.currentTimeMillis = 10

        listener.onReportingEvent(
            event = ReportingEvent.Dismiss(
                data = ReportingEvent.DismissData.TimedOut,
                displayTime = 10.milliseconds,
                context = defaultLayoutData
            )
        )

        verifyEvents(listOf(
            Pair(
                InAppResolutionEvent.timedOut(10),
                defaultLayoutData
            )
        ))

        assertFalse(timer.isStarted)
        assertEquals(displayResult, DisplayResult.FINISHED)
    }

    private fun makePagerInfo(identifier: String, page: Int): PagerData {
        return PagerData(identifier, page, "page-$page", 100, false)
    }

    private fun verifyEvents(expected: List<Pair<InAppEvent, LayoutData?>>) {
        assertEquals(recordedEvents.size, expected.size)

        expected.forEachIndexed { index, event ->
            val recorded = recordedEvents[index]
            assertEquals(event.first.eventType, recorded.first.eventType)
            assertEquals(event.first.data?.toJsonValue(), recorded.first.data?.toJsonValue())
            assertEquals(event.second, recorded.second)
        }
    }
}

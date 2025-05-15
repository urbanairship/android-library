package com.urbanairship.iam.adapter

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.event.ReportingEvent.FormResult
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.android.layout.reporting.ThomasFormField
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
import kotlin.time.Duration.Companion.milliseconds
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class LayoutAdapterListenerTest {
    private val analytics: InAppMessageAnalyticsInterface = mockk()
    private var recordedEvents = mutableListOf<Pair<InAppEvent, LayoutData?>>()
    private var displayResult: DisplayResult? = null
    private val listener = LayoutListener(analytics) { displayResult = it }
    private val defaultLayoutData = LayoutData(null, null, "button")

    @Before
    public fun setup() {
        every { analytics.recordEvent(any(), any()) } answers {
            recordedEvents.add(Pair(firstArg(), secondArg()))
        }
    }

    @Test
    public fun testFormSubmitted() {
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

        assertNull(displayResult)
    }

    @Test
    public fun testFormDisplayed() {
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

        assertNull(displayResult)
    }

    @Test
    public fun testButtonTap() {
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

        assertNull(displayResult)
    }

    @Test
    public fun testDismissed() {
        listener.onDismiss(false)
        assertEquals(displayResult, DisplayResult.FINISHED)
    }

    @Test
    public fun testCancelled() {
        listener.onDismiss(true)
        assertEquals(displayResult, DisplayResult.CANCEL)
    }

    @Test
    public fun testButtonDismissEvent() {
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
                    displayTime = 10.milliseconds
                ),
                defaultLayoutData
            )
        ))
    }

    @Test
    public fun testUserDismissedEvent() {
        listener.onReportingEvent(
            event = ReportingEvent.Dismiss(
                data = ReportingEvent.DismissData.UserDismissed,
                displayTime = 5.milliseconds,
                context = defaultLayoutData
            )
        )

        verifyEvents(listOf(
            Pair(
                InAppResolutionEvent.userDismissed(
                    displayTime = 10.milliseconds
                ),
                defaultLayoutData
            )
        ))
    }

    @Test
    public fun testPageView() {
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
    }

    @Test
    public fun testPageGesture() {
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
    }

    @Test
    public fun testPageAction() {
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
    }

    @Test
    public fun testPageSwipe() {
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
    }

    @Test
    public fun testPagerSummary() {

        val page0 = makePagerInfo("foo", 0)
        val page1 = makePagerInfo("foo", 1)

        val data = ReportingEvent.PageSummaryData(
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

        listener.onReportingEvent(
            event = ReportingEvent.PagerSummary(
                data = data,
                context = defaultLayoutData
            )
        )

        verifyEvents(listOf(
            Pair(InAppPagerSummaryEvent(data), defaultLayoutData))
        )
    }

    @Test
    public fun testVisibilityChanged() {
        listener.onVisibilityChanged(isVisible = true, isForegrounded = true)
        verifyEvents(listOf(
            Pair(InAppDisplayEvent(), null)
        ))
        listener.onVisibilityChanged(isVisible = false, isForegrounded = true)
        verifyEvents(listOf(
            Pair(InAppDisplayEvent(), null)
        ))
        listener.onVisibilityChanged(isVisible = false, isForegrounded = false)
        verifyEvents(listOf(
            Pair(InAppDisplayEvent(), null)
        ))
        listener.onVisibilityChanged(isVisible = true, isForegrounded = false)
        verifyEvents(listOf(
            Pair(InAppDisplayEvent(), null)
        ))
        listener.onVisibilityChanged(isVisible = true, isForegrounded = true)
        verifyEvents(listOf(
            Pair(InAppDisplayEvent(), null),
            Pair(InAppDisplayEvent(), null)
        ))
        assertNull(displayResult)
    }

    @Test
    public fun testTimedOutEvent() {
        listener.onReportingEvent(
            event = ReportingEvent.Dismiss(
                data = ReportingEvent.DismissData.TimedOut,
                displayTime = 10.milliseconds,
                context = defaultLayoutData
            )
        )

        verifyEvents(listOf(
            Pair(
                InAppResolutionEvent.timedOut(10.milliseconds),
                defaultLayoutData
            )
        ))
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

package com.urbanairship.automation.rewrite.inappmessage.displayadapter

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestActivityMonitor
import com.urbanairship.TestClock
import com.urbanairship.android.layout.event.ReportingEvent.FormResult
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.android.layout.reporting.FormInfo
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.automation.rewrite.inappmessage.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppButtonTapEvent
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppEvent
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppFormDisplayEvent
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppFormResultEvent
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppGestureEvent
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppPageActionEvent
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppPageSwipeEvent
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppPageViewEvent
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppPagerSummaryEvent
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppPermissionResultEvent
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppResolutionEvent
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.PageViewSummary
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.layout.LayoutAdapter
import com.urbanairship.automation.rewrite.utils.ActiveTimer
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
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
    private val activityMonitor = TestActivityMonitor()
    private val clock = TestClock()
    private val timer = ActiveTimer(activityMonitor, clock)
    private var displayResult: DisplayResult? = null
    private val displayListener = InAppMessageDisplayListener(analytics, timer, { displayResult = it })
    private val listener = LayoutAdapter.Listener(displayListener)
    private val defaultLayoutData = LayoutData(null, null, "button")

    @Before
    public fun setup() {
        every { analytics.recordEvent(any(), any()) } answers {
            recordedEvents.add(Pair(firstArg(), secondArg()))
        }

        coEvery { analytics.recordImpression() } just runs
    }

    @Test
    public fun testFormSubmitted() {
        clock.currentTimeMillis = 0
        timer.start()
        assertTrue(timer.isStarted)

        val form = FormResult(FormData.Form(
            identifier = "form id",
            responseType = null,
            children = emptySet()
            ), FormInfo("form id", "", null, true),
            mapOf()
        )

        listener.onFormResult(form.formData, defaultLayoutData)

        verifyEvents(listOf(
            Pair(InAppFormResultEvent(
                jsonMapOf(
                    "form id" to mapOf(
                        "type" to "form",
                        "children" to emptyMap<String, String>()
                    )
                ).toJsonValue()
            ), defaultLayoutData)
        ))

        assertTrue(timer.isStarted)
        assertNull(displayResult)
    }

    @Test
    public fun testFormDisplayed() {
        clock.currentTimeMillis = 0
        timer.start()

        val form = FormInfo("form id", "some type", "some response type", true)
        listener.onFormDisplay(form, defaultLayoutData)

        verifyEvents(listOf(
            Pair(InAppFormDisplayEvent(form), defaultLayoutData)
        ))

        assertTrue(timer.isStarted)
        assertNull(displayResult)
    }

    @Test
    public fun testButtonTap() {
        timer.start()

        listener.onButtonTap(
            buttonId = "button id",
            reportingMetadata = JsonValue.wrap("some metadata"),
            state = defaultLayoutData
        )

        verifyEvents(listOf(
            Pair(InAppButtonTapEvent("button id", JsonValue.wrap("some metadata")),
                defaultLayoutData)
        ))

        assertTrue(timer.isStarted)
        assertNull(displayResult)
    }

    @Test
    public fun testDismissed() {
        clock.currentTimeMillis = 0
        timer.start()
        clock.currentTimeMillis = 10


        listener.onDismiss(5)
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

        listener.onDismiss(
            buttonId = "button id",
            buttonDescription = "button description",
            cancel = false,
            displayTime = 10,
            state = defaultLayoutData
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

        listener.onDismiss(
            buttonId = "button id",
            buttonDescription = "button description",
            cancel = true,
            displayTime = 5,
            state = defaultLayoutData
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
        listener.onPageView(pageInfo, defaultLayoutData, 10)

        verifyEvents(listOf(
            Pair(
                InAppPageViewEvent(pageInfo, 1),
                defaultLayoutData
            )
        ))

        assertTrue(timer.isStarted)
        assertNull(displayResult)
    }

    @Test
    public fun testPageGesture() {
        timer.start()

        listener.onPagerGesture(
            gestureId = "gesture id",
            reportingMetadata = JsonValue.wrap("some metadata"),
            state = defaultLayoutData)

        verifyEvents(listOf(
            Pair(
                InAppGestureEvent("gesture id", JsonValue.wrap("some metadata")),
                defaultLayoutData
            )
        ))

        assertTrue(timer.isStarted)
        assertNull(displayResult)
    }

    @Test
    public fun testPageAction() {
        timer.start()
        listener.onPagerAutomatedAction(
            actionId = "action id",
            reportingMetadata = JsonValue.wrap("some metadata"),
            state = defaultLayoutData
        )

        verifyEvents(listOf(
            Pair(
                InAppPageActionEvent("action id", JsonValue.wrap("some metadata")),
                defaultLayoutData
            )
        ))

        assertTrue(timer.isStarted)
        assertNull(displayResult)
    }

    @Test
    public fun testPageSwipe() {
        timer.start()
        val pager = makePagerInfo("foo", 0)

        listener.onPageSwipe(pager, 1, "page-1", 0, "page-0", defaultLayoutData)

        verifyEvents(listOf(
            Pair(
                InAppPageSwipeEvent(makePagerInfo("foo", 0), makePagerInfo("foo", 1)),
                defaultLayoutData
            )
        ))

        assertTrue(timer.isStarted)
        assertNull(displayResult)
    }

    @Test
    public fun testPromptPermissionResult() {
        timer.start()

        listener.onPromptPermissionResult(
            permission = Permission.DISPLAY_NOTIFICATIONS,
            before = PermissionStatus.DENIED,
            after = PermissionStatus.GRANTED,
            layoutContext = defaultLayoutData)

        verifyEvents(listOf(
            Pair(
                InAppPermissionResultEvent(
                    permission = Permission.DISPLAY_NOTIFICATIONS,
                    startingStatus = PermissionStatus.DENIED,
                    endingStatus = PermissionStatus.GRANTED
                ),
                defaultLayoutData
            )
        ))

        assertTrue(timer.isStarted)
        assertNull(displayResult)
    }

    @Test
    public fun testDismissPagerSummary() {
        clock.currentTimeMillis = 0
        timer.start()
        val page0 = makePagerInfo("foo", 0)
        val page1 = makePagerInfo("foo", 1)

        listener.onPageView(page0, defaultLayoutData, 5)
        listener.onPageView(page1, defaultLayoutData, 10)
        listener.onPageView(page0, defaultLayoutData, 20)
        listener.onDismiss(40)

        val expected = listOf(
            Pair(InAppPageViewEvent(page0, 1), defaultLayoutData),
            Pair(InAppPageViewEvent(page1, 1), defaultLayoutData),
            Pair(InAppPageViewEvent(page0, 2), defaultLayoutData),
            Pair(
                InAppPagerSummaryEvent(
                    pagerData = page0,
                    viewedPages = listOf(
                        PageViewSummary("page-0", 0, 5),
                        PageViewSummary("page-1", 1, 10),
                        PageViewSummary("page-0", 0, 20),
                    )
                ),
                null
            ),
            Pair(InAppResolutionEvent.userDismissed(30), null)
        )
        verifyEvents(expected)

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
            assertEquals(event.first.name, recorded.first.name)
            assertEquals(event.first.data?.toJsonValue(), recorded.first.data?.toJsonValue())
            assertEquals(event.second, recorded.second)
        }
    }
}

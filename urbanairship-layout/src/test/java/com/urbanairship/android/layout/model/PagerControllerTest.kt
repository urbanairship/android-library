/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import androidx.core.util.ObjectsCompat
import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.ModelProvider
import com.urbanairship.android.layout.event.ButtonEvent.PagerNext
import com.urbanairship.android.layout.event.ButtonEvent.PagerPrevious
import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.event.EventType
import com.urbanairship.android.layout.event.PagerEvent
import com.urbanairship.android.layout.event.PagerEvent.IndicatorInit
import com.urbanairship.android.layout.event.PagerEvent.PageActions
import com.urbanairship.android.layout.event.PagerEvent.Scroll
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.event.ReportingEvent.ButtonTap
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import test.TestEventListener

@RunWith(RobolectricTestRunner::class)
public class PagerControllerTest {

    private lateinit var controller: PagerController
    private lateinit var testListener: TestEventListener

    private val mockPager = mockk<PagerModel>(relaxed = true)
    private val mockIndicator = mockk<PagerIndicatorModel>(relaxed = true)
    private val mockEnv = spyk(ModelEnvironment(ModelProvider(), emptyMap()))
    private val mockButton = mockk<ButtonModel>()

    private val mockView: LayoutModel = spyk(
        object : LayoutModel(
            viewType = ViewType.CONTAINER,
            environment = mockEnv
        ) {
            override val children = listOf(mockPager, mockIndicator)
        }
    )

    @Before
    public fun setUp() {
        every { mockPager.viewType } returns ViewType.PAGER
        every { mockIndicator.viewType } returns ViewType.PAGER_INDICATOR
        every { mockPager.items } returns ITEMS
        every { mockButton.identifier } returns BUTTON_ID
        every { mockButton.reportingDescription() } returns BUTTON_DESCRIPTION

        controller = PagerController(
            view = mockView,
            identifier = PAGER_ID,
            environment = mockEnv
        )
        testListener = TestEventListener()
        controller.addListener(testListener)
    }

    @Test
    public fun testViewInit() {
        val pagerInitEvent = PagerEvent.Init(mockPager, 0, PAGE_1_ID, EMPTY_ACTIONS, 0)
        controller.onEvent(IndicatorInit(mockIndicator), LayoutData.empty())
        controller.onEvent(pagerInitEvent, LayoutData.empty())

        // Make sure the indicator received the init event from the pager.
        verify { mockIndicator.trickleEvent(pagerInitEvent, any()) }

        // Verify reporting event was sent for the initial page view.
        assertEquals(1, testListener.getCount(EventType.REPORTING_EVENT).toLong())

        val layoutData = ObjectsCompat.requireNonNull(
            testListener.getLayoutDataAt(EventType.REPORTING_EVENT, 0)
        )
        val pagerData = ObjectsCompat.requireNonNull(layoutData.pagerData)

        assertEquals(PAGER_ID, pagerData.identifier)
        assertEquals(PAGE_1_ID, pagerData.pageId)
        assertEquals(0, pagerData.index.toLong())
        assertFalse(pagerData.isCompleted)
    }

    @Test
    public fun testOverrideState() {
        controller.onEvent(IndicatorInit(mockIndicator), LayoutData.empty())
        controller.onEvent(
            PagerEvent.Init(mockPager, 0, PAGE_1_ID, EMPTY_ACTIONS, 0),
            LayoutData.empty()
        )
        assertEquals(1, testListener.getCount(EventType.REPORTING_EVENT).toLong())

        // Bubble a Reporting Event to the controller.
        controller.onEvent(ButtonTap("buttonId"), LayoutData.empty())

        // Verify that the listener was notified and pager data was added to the event.
        assertEquals(2, testListener.getCount(EventType.REPORTING_EVENT).toLong())
        val reportingEvent =
            testListener.getEventAt(EventType.REPORTING_EVENT, 1) as ReportingEvent
        val layoutData = ObjectsCompat.requireNonNull(
            testListener.getLayoutDataAt(
                EventType.REPORTING_EVENT,
                0
            )
        )
        assertEquals(ReportingEvent.ReportType.BUTTON_TAP, reportingEvent.reportType)

        val pagerData = ObjectsCompat.requireNonNull(layoutData.pagerData)
        assertEquals(PAGER_ID, pagerData.identifier)
        assertEquals(PAGE_1_ID, pagerData.pageId)
        assertEquals(0, pagerData.index.toLong())
        assertFalse(pagerData.isCompleted)
    }

    @Test
    public fun testPagerNextPreviousButtonBehaviors() {
        controller.onEvent(IndicatorInit(mockIndicator), LayoutData.empty())
        controller.onEvent(
            PagerEvent.Init(mockPager, 0, PAGE_1_ID, EMPTY_ACTIONS, 0),
            LayoutData.empty()
        )
        assertEquals(1, testListener.getCount(EventType.REPORTING_EVENT).toLong())

        // Simulate a button tap with a next behavior and make sure the controller trickled to the pager and indicator.
        val pagerNext: Event = PagerNext(mockButton)
        controller.onEvent(pagerNext, LayoutData.empty())
        verify { mockPager.trickleEvent(pagerNext, any()) }
        verify { mockIndicator.trickleEvent(pagerNext, any()) }

        // Repeat with a pager previous button.
        val pagerPrev: Event = PagerPrevious(mockButton)
        controller.onEvent(pagerPrev, LayoutData.empty())
        verify { mockPager.trickleEvent(pagerPrev, any()) }
        verify { mockIndicator.trickleEvent(pagerPrev, any()) }

        // Check emitted reporting events. The mocked pager doesn't move between pager pages when handling the button
        // event, so we don't expect any further events after the initial page view.
        assertEquals(1, testListener.count.toLong())
    }

    @Test
    public fun testPagerPageActions() {
        val firstPageActions = mapOf("add_tags_action" to JsonValue.wrapOpt("page-1"))

        val secondPageActions = mapOf("add_tags_action" to JsonValue.wrapOpt("page-2"))

        // Verify actions are bubbled up from the init event
        controller.onEvent(
            PagerEvent.Init(mockPager, 0, PAGE_1_ID, firstPageActions, 0L),
            LayoutData.empty()
        )
        assertEquals(1, testListener.getCount(EventType.PAGER_PAGE_ACTIONS).toLong())
        val event1: PageActions = testListener.getEventAt(EventType.PAGER_PAGE_ACTIONS, 0) as PageActions
        assertEquals(firstPageActions, event1.actions)

        // Verify that scrolling to the second page bubbles up actions
        controller.onEvent(
            Scroll(
                mockPager,
                1,
                PAGE_2_ID,
                secondPageActions,
                0,
                PAGE_1_ID,
                false,
                0L
            ), LayoutData.empty()
        )
        assertEquals(2, testListener.getCount(EventType.PAGER_PAGE_ACTIONS).toLong())
        val event2 = testListener.getEventAt(EventType.PAGER_PAGE_ACTIONS, 1) as PageActions
        assertEquals(secondPageActions, event2.actions)

        // Verify scrolling to the third page doesn't bubble actions (because there aren't any)
        controller.onEvent(
            Scroll(
                mockPager,
                2,
                PAGE_3_ID,
                EMPTY_ACTIONS,
                1,
                PAGE_2_ID,
                false,
                0L
            ), LayoutData.empty()
        )
        assertEquals(2, testListener.getCount(EventType.PAGER_PAGE_ACTIONS).toLong())

        // Verify that scrolling back to the second page bubbles up actions again
        controller.onEvent(
            Scroll(
                mockPager,
                1,
                PAGE_2_ID,
                secondPageActions,
                2,
                PAGE_3_ID,
                false,
                0L
            ), LayoutData.empty()
        )
        assertEquals(3, testListener.getCount(EventType.PAGER_PAGE_ACTIONS).toLong())
        val event3 = testListener.getEventAt(EventType.PAGER_PAGE_ACTIONS, 2) as PageActions
        assertEquals(secondPageActions, event3.actions)
    }

    private companion object {
        private const val PAGER_ID = "pager-identifier"
        private const val PAGE_1_ID = "page-one-identifier"
        private const val PAGE_2_ID = "page-two-identifier"
        private const val PAGE_3_ID = "page-two-identifier"
        private const val BUTTON_ID = "button-identifier"
        private const val BUTTON_DESCRIPTION = "button-description"
        private val EMPTY_ACTIONS = mapOf<String, JsonValue>()
        private val ITEMS = listOf(
            PagerModel.Item(mockk(), PAGE_1_ID, EMPTY_ACTIONS),
            PagerModel.Item(mockk(), PAGE_2_ID, EMPTY_ACTIONS),
            PagerModel.Item(mockk(), PAGE_3_ID, EMPTY_ACTIONS)
        )
    }
}

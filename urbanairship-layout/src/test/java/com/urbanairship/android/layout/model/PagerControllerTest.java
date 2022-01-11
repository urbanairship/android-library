/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.event.ButtonEvent;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.EventType;
import com.urbanairship.android.layout.event.PagerEvent;
import com.urbanairship.android.layout.event.ReportingEvent;
import com.urbanairship.android.layout.event.WebViewEvent;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.PagerData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Arrays;
import java.util.List;

import test.TestEventListener;

import static androidx.core.util.ObjectsCompat.requireNonNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PagerControllerTest {
    private static final String PAGER_ID = "pager-identifier";
    private static final String PAGE_1_ID = "page-one-identifier";
    private static final String PAGE_2_ID = "page-two-identifier";
    private static final String PAGE_3_ID = "page-two-identifier";
    private static final String BUTTON_ID = "button-identifier";
    private static final String BUTTON_DESCRIPTION = "button-description";

    private static final List<PagerModel.Item> ITEMS = Arrays.asList(
        new PagerModel.Item(mock(BaseModel.class), PAGE_1_ID),
        new PagerModel.Item(mock(BaseModel.class), PAGE_2_ID),
        new PagerModel.Item(mock(BaseModel.class), PAGE_3_ID)
    );

    private AutoCloseable mocksClosable;

    private PagerController controller;
    private TestEventListener testListener;

    @Mock
    private PagerModel mockPager;

    @Mock
    private PagerIndicatorModel mockIndicator;

    @Spy
    private LayoutModel mockView = new LayoutModel(ViewType.CONTAINER, null, null) {
        @Override
        public List<BaseModel> getChildren() {
            return Arrays.asList(mockPager, mockIndicator);
        }
    };

    @Mock
    private ButtonModel mockButton;

    @Before
    public void setUp() {
        mocksClosable = MockitoAnnotations.openMocks(this);

        when(mockPager.getType()).thenReturn(ViewType.PAGER);
        when(mockIndicator.getType()).thenReturn(ViewType.PAGER_INDICATOR);

        when(mockPager.getItems()).thenReturn(ITEMS);

        when(mockButton.getIdentifier()).thenReturn(BUTTON_ID);
        when(mockButton.reportingDescription()).thenReturn(BUTTON_DESCRIPTION);

        controller = new PagerController(mockView, PAGER_ID);
        testListener = new TestEventListener();
        controller.addListener(testListener);
    }

    @After
    public void tearDown() throws Exception {
        mocksClosable.close();
    }

    @Test
    public void testViewInit() {
        PagerEvent.Init pagerInitEvent = new PagerEvent.Init(mockPager, 0, PAGE_1_ID, 0);
        controller.onEvent(new PagerEvent.IndicatorInit(mockIndicator));
        controller.onEvent(pagerInitEvent);

        // Make sure the indicator received the init event from the pager.
        Mockito.verify(mockIndicator).trickleEvent(eq(pagerInitEvent));

        // Verify reporting event was sent for the initial page view.
        assertEquals(1, testListener.getCount(EventType.REPORTING_EVENT));
        ReportingEvent.PageView pageViewEvent = (ReportingEvent.PageView) testListener.getEventAt(0);
        PagerData pagerData = requireNonNull(pageViewEvent.getState().getPagerData());
        assertEquals(PAGER_ID, pagerData.getIdentifier());
        assertEquals(PAGE_1_ID, pagerData.getPageId());
        assertEquals(0, pagerData.getIndex());
        assertFalse(pagerData.isCompleted());
    }

    @Test
    public void testOverridesStateForReportingEvents() {
        controller.onEvent(new PagerEvent.IndicatorInit(mockIndicator));
        controller.onEvent(new PagerEvent.Init(mockPager, 0, PAGE_1_ID, 0));
        assertEquals(1, testListener.getCount(EventType.REPORTING_EVENT));

        // Bubble a Reporting Event to the controller.
        controller.onEvent(new ReportingEvent.ButtonTap("buttonId"));

        // Verify that the listener was notified and pager data was added to the event.
        assertEquals(2, testListener.getCount(EventType.REPORTING_EVENT));
        ReportingEvent reportingEvent = (ReportingEvent) testListener.getEventAt(EventType.REPORTING_EVENT, 1);
        assertEquals(ReportingEvent.ReportType.BUTTON_TAP, reportingEvent.getReportType());
        PagerData pagerData = requireNonNull(reportingEvent.getState().getPagerData());
        assertEquals(PAGER_ID, pagerData.getIdentifier());
        assertEquals(PAGE_1_ID, pagerData.getPageId());
        assertEquals(0, pagerData.getIndex());
        assertFalse(pagerData.isCompleted());
    }

    @Test
    public void testOverridesStateForWebViewEvents() {
        controller.onEvent(new PagerEvent.IndicatorInit(mockIndicator));
        controller.onEvent(new PagerEvent.Init(mockPager, 0, PAGE_1_ID, 0));

        // Simulate a webview close from within the pager.
        controller.onEvent(new WebViewEvent.Close());

        // Verify that the listener was notified and pager data was added to the event.
        assertEquals(1, testListener.getCount(EventType.WEBVIEW_CLOSE));
        WebViewEvent.Close closeEvent = (WebViewEvent.Close) testListener.getEventAt(EventType.WEBVIEW_CLOSE, 0);
        PagerData pagerData = requireNonNull(closeEvent.getState().getPagerData());
        assertEquals(PAGER_ID, pagerData.getIdentifier());
        assertEquals(PAGE_1_ID, pagerData.getPageId());
        assertEquals(0, pagerData.getIndex());
        assertFalse(pagerData.isCompleted());
    }

    @Test
    public void testOverridesStateForButtonEvents() {
        // Init the pager.
        controller.onEvent(new PagerEvent.IndicatorInit(mockIndicator));
        controller.onEvent(new PagerEvent.Init(mockPager, 0, PAGE_1_ID, 0));

        // Simulate a cancel event from a button within the pager.
        controller.onEvent(new ButtonEvent.Cancel(mockButton));

        // Verify that the listener was notified and pager data was added to the event.
        assertEquals(1, testListener.getCount(EventType.BUTTON_BEHAVIOR_CANCEL));
        ButtonEvent cancelEvent = (ButtonEvent) testListener.getEventAt(EventType.BUTTON_BEHAVIOR_CANCEL, 0);
        PagerData cancelPagerData = requireNonNull(cancelEvent.getState().getPagerData());
        assertEquals(EventType.BUTTON_BEHAVIOR_CANCEL, cancelEvent.getType());
        assertEquals(PAGER_ID, cancelPagerData.getIdentifier());
        assertEquals(PAGE_1_ID, cancelPagerData.getPageId());
        assertEquals(0, cancelPagerData.getIndex());
        assertFalse(cancelPagerData.isCompleted());

        // Advance to the next page to update the pager state on the controller
        controller.onEvent(new PagerEvent.Scroll(mockPager, 1, PAGE_2_ID, 0, PAGE_1_ID, false, 0L));

        // Simulate a dismiss event from a button
        controller.onEvent(new ButtonEvent.Dismiss(mockButton));

        // Verify that the listener was notified and updated pager data was added to the event
        assertEquals(1, testListener.getCount(EventType.BUTTON_BEHAVIOR_DISMISS));
        ButtonEvent dismissEvent = (ButtonEvent) testListener.getEventAt(EventType.BUTTON_BEHAVIOR_DISMISS, 0);
        assertEquals(EventType.BUTTON_BEHAVIOR_DISMISS, dismissEvent.getType());
        PagerData dismissPagerData = requireNonNull(dismissEvent.getState().getPagerData());
        assertEquals(PAGER_ID, dismissPagerData.getIdentifier());
        assertEquals(PAGE_2_ID, dismissPagerData.getPageId());
        assertEquals(1, dismissPagerData.getIndex());
        assertFalse(dismissPagerData.isCompleted());
    }

    @Test
    public void testPagerNextPreviousButtonBehaviors() {
        controller.onEvent(new PagerEvent.IndicatorInit(mockIndicator));
        controller.onEvent(new PagerEvent.Init(mockPager, 0, PAGE_1_ID, 0));
        assertEquals(1, testListener.getCount(EventType.REPORTING_EVENT));

        // Simulate a button tap with a next behavior and make sure the controller trickled to the pager and indicator.
        Event pagerNext = new ButtonEvent.PagerNext(mockButton);
        controller.onEvent(pagerNext);
        Mockito.verify(mockPager).trickleEvent(eq(pagerNext));
        Mockito.verify(mockIndicator).trickleEvent(eq(pagerNext));

        // Repeat with a pager previous button.
        Event pagerPrev = new ButtonEvent.PagerPrevious(mockButton);
        controller.onEvent(pagerPrev);
        Mockito.verify(mockPager).trickleEvent(eq(pagerPrev));
        Mockito.verify(mockIndicator).trickleEvent(eq(pagerPrev));

        // Check emitted reporting events. The mocked pager doesn't move between pager pages when handling the button
        // event, so we don't expect any further events after the initial page view.
        assertEquals(1, testListener.getCount());
    }
}

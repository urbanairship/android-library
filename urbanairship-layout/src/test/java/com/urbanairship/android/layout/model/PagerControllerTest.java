/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.event.ButtonEvent;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.EventType;
import com.urbanairship.android.layout.event.PagerEvent;
import com.urbanairship.android.layout.event.ReportingEvent;
import com.urbanairship.android.layout.event.WebViewEvent;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.android.layout.reporting.PagerData;
import com.urbanairship.json.JsonValue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import test.TestEventListener;

import static androidx.core.util.ObjectsCompat.requireNonNull;
import static com.urbanairship.android.layout.event.EventType.PAGER_PAGE_ACTIONS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class PagerControllerTest {
    private static final String PAGER_ID = "pager-identifier";
    private static final String PAGE_1_ID = "page-one-identifier";
    private static final String PAGE_2_ID = "page-two-identifier";
    private static final String PAGE_3_ID = "page-two-identifier";
    private static final String BUTTON_ID = "button-identifier";
    private static final String BUTTON_DESCRIPTION = "button-description";
    private static final Map<String, JsonValue> EMPTY_ACTIONS = Collections.emptyMap();


    private static final List<PagerModel.Item> ITEMS = Arrays.asList(
        new PagerModel.Item(mock(BaseModel.class), PAGE_1_ID, EMPTY_ACTIONS),
        new PagerModel.Item(mock(BaseModel.class), PAGE_2_ID, EMPTY_ACTIONS),
        new PagerModel.Item(mock(BaseModel.class), PAGE_3_ID, EMPTY_ACTIONS)
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
        PagerEvent.Init pagerInitEvent = new PagerEvent.Init(mockPager, 0, PAGE_1_ID, EMPTY_ACTIONS, 0);
        controller.onEvent(new PagerEvent.IndicatorInit(mockIndicator), LayoutData.empty());
        controller.onEvent(pagerInitEvent, LayoutData.empty());

        // Make sure the indicator received the init event from the pager.
        Mockito.verify(mockIndicator).trickleEvent(eq(pagerInitEvent), any());

        // Verify reporting event was sent for the initial page view.
        assertEquals(1, testListener.getCount(EventType.REPORTING_EVENT));
        ReportingEvent.PageView pageViewEvent = (ReportingEvent.PageView) testListener.getEventAt(0);
        LayoutData layoutData = requireNonNull(testListener.getLayoutDataAt(EventType.REPORTING_EVENT, 0));
        PagerData pagerData = requireNonNull(layoutData.getPagerData());
        assertEquals(PAGER_ID, pagerData.getIdentifier());
        assertEquals(PAGE_1_ID, pagerData.getPageId());
        assertEquals(0, pagerData.getIndex());
        assertFalse(pagerData.isCompleted());
    }

    @Test
    public void testOverrideState() {
        controller.onEvent(new PagerEvent.IndicatorInit(mockIndicator), LayoutData.empty());
        controller.onEvent(new PagerEvent.Init(mockPager, 0, PAGE_1_ID, EMPTY_ACTIONS, 0), LayoutData.empty());
        assertEquals(1, testListener.getCount(EventType.REPORTING_EVENT));

        // Bubble a Reporting Event to the controller.
        controller.onEvent(new ReportingEvent.ButtonTap("buttonId"), LayoutData.empty());

        // Verify that the listener was notified and pager data was added to the event.
        assertEquals(2, testListener.getCount(EventType.REPORTING_EVENT));
        ReportingEvent reportingEvent = (ReportingEvent) testListener.getEventAt(EventType.REPORTING_EVENT, 1);
        LayoutData layoutData = requireNonNull(testListener.getLayoutDataAt(EventType.REPORTING_EVENT, 0));

        assertEquals(ReportingEvent.ReportType.BUTTON_TAP, reportingEvent.getReportType());
        PagerData pagerData = requireNonNull(layoutData.getPagerData());
        assertEquals(PAGER_ID, pagerData.getIdentifier());
        assertEquals(PAGE_1_ID, pagerData.getPageId());
        assertEquals(0, pagerData.getIndex());
        assertFalse(pagerData.isCompleted());
    }

    @Test
    public void testPagerNextPreviousButtonBehaviors() {
        controller.onEvent(new PagerEvent.IndicatorInit(mockIndicator), LayoutData.empty());
        controller.onEvent(new PagerEvent.Init(mockPager, 0, PAGE_1_ID, EMPTY_ACTIONS, 0), LayoutData.empty());
        assertEquals(1, testListener.getCount(EventType.REPORTING_EVENT));

        // Simulate a button tap with a next behavior and make sure the controller trickled to the pager and indicator.
        Event pagerNext = new ButtonEvent.PagerNext(mockButton);
        controller.onEvent(pagerNext, LayoutData.empty());
        Mockito.verify(mockPager).trickleEvent(eq(pagerNext), any());
        Mockito.verify(mockIndicator).trickleEvent(eq(pagerNext), any());

        // Repeat with a pager previous button.
        Event pagerPrev = new ButtonEvent.PagerPrevious(mockButton);
        controller.onEvent(pagerPrev, LayoutData.empty());
        Mockito.verify(mockPager).trickleEvent(eq(pagerPrev), any());
        Mockito.verify(mockIndicator).trickleEvent(eq(pagerPrev), any());

        // Check emitted reporting events. The mocked pager doesn't move between pager pages when handling the button
        // event, so we don't expect any further events after the initial page view.
        assertEquals(1, testListener.getCount());
    }

    @Test
    public void testPagerPageActions() {
        Map<String, JsonValue> firstPageActions = new HashMap<String, JsonValue>() {{
            put("add_tags_action", JsonValue.wrapOpt("page-1"));
        }};
        Map<String, JsonValue> secondPageActions = new HashMap<String, JsonValue>() {{
            put("add_tags_action", JsonValue.wrapOpt("page-2"));
        }};

        PagerEvent.PageActions event;

        // Verify actions are bubbled up from the init event
        controller.onEvent(new PagerEvent.Init(mockPager, 0, PAGE_1_ID, firstPageActions, 0L), LayoutData.empty());
        assertEquals(1, testListener.getCount(PAGER_PAGE_ACTIONS));
        event = (PagerEvent.PageActions) testListener.getEventAt(PAGER_PAGE_ACTIONS, 0);
        assertEquals(firstPageActions, event.getActions());

        // Verify that scrolling to the second page bubbles up actions
        controller.onEvent(new PagerEvent.Scroll(mockPager, 1, PAGE_2_ID, secondPageActions, 0, PAGE_1_ID, false, 0L), LayoutData.empty());
        assertEquals(2, testListener.getCount(PAGER_PAGE_ACTIONS));
        event = (PagerEvent.PageActions) testListener.getEventAt(PAGER_PAGE_ACTIONS, 1);
        assertEquals(secondPageActions, event.getActions());

        // Verify scrolling to the third page doesn't bubble actions (because there aren't any)
        controller.onEvent(new PagerEvent.Scroll(mockPager, 2, PAGE_3_ID, EMPTY_ACTIONS, 1, PAGE_2_ID, false, 0L), LayoutData.empty());
        assertEquals(2, testListener.getCount(PAGER_PAGE_ACTIONS));

        // Verify that scrolling back to the second page bubbles up actions again
        controller.onEvent(new PagerEvent.Scroll(mockPager, 1, PAGE_2_ID, secondPageActions, 2, PAGE_3_ID, false, 0L), LayoutData.empty());
        assertEquals(3, testListener.getCount(PAGER_PAGE_ACTIONS));
        event = (PagerEvent.PageActions) testListener.getEventAt(PAGER_PAGE_ACTIONS, 2);
        assertEquals(secondPageActions, event.getActions());
    }
}

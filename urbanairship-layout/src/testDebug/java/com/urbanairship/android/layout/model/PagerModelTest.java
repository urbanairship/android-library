/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.event.PagerEvent;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import test.TestEventListener;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

@RunWith(RobolectricTestRunner.class)
public class PagerModelTest {
    private static final String PAGE_1_ID = "page-one-identifier";
    private static final String PAGE_2_ID = "page-two-identifier";
    private static final String PAGE_3_ID = "page-two-identifier";
    private static final Map<String, JsonValue> EMPTY_ACTIONS = Collections.emptyMap();

    private static final List<PagerModel.Item> ITEMS = Arrays.asList(
        new PagerModel.Item(mock(BaseModel.class), PAGE_1_ID, EMPTY_ACTIONS),
        new PagerModel.Item(mock(BaseModel.class), PAGE_2_ID, EMPTY_ACTIONS),
        new PagerModel.Item(mock(BaseModel.class), PAGE_3_ID, EMPTY_ACTIONS)
    );

    private PagerModel pagerModel;

    private TestEventListener testListener;

    @Before
    public void setup() {
        pagerModel = Mockito.spy(new PagerModel(ITEMS, false, null, null));

        testListener = new TestEventListener();
        pagerModel.addListener(testListener);

        pagerModel.onConfigured(0, 0L);
        // Sanity check init event.
        assertEquals(1, testListener.getCount());

    }

    @Test
    public void testUserScroll() {
        // Simulate a user swipe (internalScroll = false).
        pagerModel.onScrollTo(1, false, 1L);
        assertEquals(2, testListener.getCount());

        // User scrolls are not internal, which results in a page swipe event.
        boolean isInternal = false;
        PagerEvent.Scroll scroll = (PagerEvent.Scroll) testListener.getEventAt(1);
        verifyPagerScroll(scroll, 0, PAGE_1_ID, 1, PAGE_2_ID, true, true, isInternal);
    }

    @Test
    public void testButtonEventScroll() {
        // Simulate a button next (internalScroll = true).
        pagerModel.onScrollTo(1, true, 1L);
        assertEquals(2, testListener.getCount());

        // Button next/previous scrolls are internal, which should not report a page swipe event.
        boolean isInternal = true;
        PagerEvent.Scroll scroll = (PagerEvent.Scroll) testListener.getEventAt(1);
        verifyPagerScroll(scroll, 0, PAGE_1_ID, 1, PAGE_2_ID, true, true, isInternal);
    }

    @Test
    public void testScrollEventHasNextAndPrevious() {
        // Scroll to the middle page (has both previous and next).
        pagerModel.onScrollTo(1, true, 1L);
        PagerEvent.Scroll scroll1 = (PagerEvent.Scroll) testListener.getEventAt(1);
        verifyPagerScroll(scroll1, 0, PAGE_1_ID, 1, PAGE_2_ID, true, true, true);

        // Scroll to the last page (has only previous).
        pagerModel.onScrollTo(2, true, 1L);
        PagerEvent.Scroll scroll2 = (PagerEvent.Scroll) testListener.getEventAt(2);
        verifyPagerScroll(scroll2, 1, PAGE_2_ID, 2, PAGE_3_ID, false, true, true);

        // Scroll back to the first page (has only next).
        pagerModel.onScrollTo(1, true, 1L);
        pagerModel.onScrollTo(0, true, 1L);
        PagerEvent.Scroll scroll3 = (PagerEvent.Scroll) testListener.getEventAt(4);
        verifyPagerScroll(scroll3, 1, PAGE_2_ID, 0, PAGE_1_ID, true, false, true);
    }

    @SuppressWarnings("SameParameterValue")
    private void verifyPagerScroll(
        PagerEvent.Scroll scroll,
        int previousPageIndex,
        String previousPageId,
        int pageIndex,
        String pageId,
        boolean hasNext,
        boolean hasPrevious,
        boolean isInternal
    ) {
        assertEquals("previousPageIndex", previousPageIndex, scroll.getPreviousPageIndex());
        assertEquals("previousPageId", previousPageId, scroll.getPreviousPageId());
        assertEquals("pageIndex", pageIndex, scroll.getPageIndex());
        assertEquals("pageId", pageId, scroll.getPageId());
        assertEquals("hasNext", hasNext, scroll.hasNext());
        assertEquals("hasPrevious", hasPrevious, scroll.hasPrevious());
        assertEquals("isInternal", isInternal, scroll.isInternal());
    }
}

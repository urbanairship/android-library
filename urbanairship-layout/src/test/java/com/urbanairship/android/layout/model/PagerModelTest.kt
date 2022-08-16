/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.ModelProvider
import com.urbanairship.android.layout.event.PagerEvent.Scroll
import com.urbanairship.json.JsonValue
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import test.TestEventListener

@RunWith(RobolectricTestRunner::class)
public class PagerModelTest {

    private val mockEnv = spyk(ModelEnvironment(ModelProvider(), emptyMap()))

    private val pagerModel = spyk(PagerModel(
        items = ITEMS,
        isSwipeDisabled = false,
        environment = mockEnv
    ))

    private val testListener = TestEventListener()

    @Before
    public fun setup() {
        pagerModel.addListener(testListener)
        pagerModel.onConfigured(0, 0L)
        // Sanity check init event.
        assertEquals(1, testListener.count.toLong())
    }

    @Test
    public fun testUserScroll() {
        // Simulate a user swipe (internalScroll = false).
        pagerModel.onScrollTo(1, false, 1L)
        assertEquals(2, testListener.count.toLong())

        // User scrolls are not internal, which results in a page swipe event.
        val isInternal = false
        val scroll = testListener.getEventAt(1) as Scroll
        verifyPagerScroll(scroll, 0, PAGE_1_ID, 1, PAGE_2_ID, true, true, isInternal)
    }

    @Test
    public fun testButtonEventScroll() {
        // Simulate a button next (internalScroll = true).
        pagerModel.onScrollTo(1, true, 1L)
        assertEquals(2, testListener.count.toLong())

        // Button next/previous scrolls are internal, which should not report a page swipe event.
        val isInternal = true
        val scroll = testListener.getEventAt(1) as Scroll
        verifyPagerScroll(scroll, 0, PAGE_1_ID, 1, PAGE_2_ID, true, true, isInternal)
    }

    @Test
    public fun testScrollEventHasNextAndPrevious() {
        // Scroll to the middle page (has both previous and next).
        pagerModel.onScrollTo(1, true, 1L)
        val scroll1 = testListener.getEventAt(1) as Scroll
        verifyPagerScroll(scroll1, 0, PAGE_1_ID, 1, PAGE_2_ID, true, true, true)

        // Scroll to the last page (has only previous).
        pagerModel.onScrollTo(2, true, 1L)
        val scroll2 = testListener.getEventAt(2) as Scroll
        verifyPagerScroll(scroll2, 1, PAGE_2_ID, 2, PAGE_3_ID, false, true, true)

        // Scroll back to the first page (has only next).
        pagerModel.onScrollTo(1, true, 1L)
        pagerModel.onScrollTo(0, true, 1L)
        val scroll3 = testListener.getEventAt(4) as Scroll
        verifyPagerScroll(scroll3, 1, PAGE_2_ID, 0, PAGE_1_ID, true, false, true)
    }

    private fun verifyPagerScroll(
        scroll: Scroll,
        previousPageIndex: Int,
        previousPageId: String,
        pageIndex: Int,
        pageId: String,
        hasNext: Boolean,
        hasPrevious: Boolean,
        isInternal: Boolean
    ) {
        assertEquals(
            "previousPageIndex",
            previousPageIndex.toLong(),
            scroll.previousPageIndex.toLong()
        )
        assertEquals("previousPageId", previousPageId, scroll.previousPageId)
        assertEquals("pageIndex", pageIndex.toLong(), scroll.pageIndex.toLong())
        assertEquals("pageId", pageId, scroll.pageId)
        assertEquals("hasNext", hasNext, scroll.hasNext())
        assertEquals("hasPrevious", hasPrevious, scroll.hasPrevious())
        assertEquals("isInternal", isInternal, scroll.isInternal)
    }

    private companion object {
        private const val PAGE_1_ID = "page-one-identifier"
        private const val PAGE_2_ID = "page-two-identifier"
        private const val PAGE_3_ID = "page-two-identifier"
        private val EMPTY_ACTIONS = mapOf<String, JsonValue>()
        private val ITEMS = listOf(
            PagerModel.Item(mockk(relaxed = true), PAGE_1_ID, EMPTY_ACTIONS),
            PagerModel.Item(mockk(relaxed = true), PAGE_2_ID, EMPTY_ACTIONS),
            PagerModel.Item(mockk(relaxed = true), PAGE_3_ID, EMPTY_ACTIONS)
        )
    }
}

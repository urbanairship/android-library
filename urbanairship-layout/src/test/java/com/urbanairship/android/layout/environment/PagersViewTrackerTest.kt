package com.urbanairship.android.layout.environment

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.event.ReportingEvent
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class PagersViewTrackerTest {

    private val tracker = PagersViewTracker()

    @Test
    public fun `onPageView tracks multiple page views for multiple pagers`() {
        val pageEvent11 = ReportingEvent.PageViewData("pager1", "pageA", 0, 3, 2, false)
        val pageEvent12 = ReportingEvent.PageViewData("pager1", "pageB", 1, 3, 2, false)

        val pageEvent21 = ReportingEvent.PageViewData("pager2", "pageA", 0, 3, 2, false)
        val pageEvent22 = ReportingEvent.PageViewData("pager2", "pageB", 1, 3, 2, false)

        tracker.onPageView(pageEvent11, 1.seconds)

        assertEquals(
            listOf(
                ReportingEvent.PageSummaryData(
                    identifier = "pager1",
                    viewedPages = emptyList(),
                    pageCount = 2,
                    completed = false
                )
            ),
            tracker.generateSummaryEvents()
        )

        tracker.onPageView(pageEvent12, 3.seconds) // Page A viewed for 2s (3s - 1s)

        assertEquals(
            listOf(
                ReportingEvent.PageSummaryData(
                    identifier = "pager1",
                    viewedPages = listOf(
                        ReportingEvent.PageSummaryData.PageView(
                            identifier = "pageA", index = 0, displayTime = 2.seconds
                        )
                    ),
                    pageCount = 2,
                    completed = false
                )
            ),
            tracker.generateSummaryEvents()
        )

        tracker.onPageView(pageEvent21, 6.seconds)

        assertEquals(
            listOf(
                ReportingEvent.PageSummaryData(
                    identifier = "pager1",
                    viewedPages = listOf(
                        ReportingEvent.PageSummaryData.PageView(
                            identifier = "pageA", index = 0, displayTime = 2.seconds
                        )
                    ),
                    pageCount = 2,
                    completed = false
                ),
                ReportingEvent.PageSummaryData(
                    identifier = "pager2",
                    viewedPages = emptyList(),
                    pageCount = 2,
                    completed = false)
            ),
            tracker.generateSummaryEvents()
        )

        tracker.onPageView(pageEvent22, 9.seconds)
        assertEquals(
            listOf(
                ReportingEvent.PageSummaryData(
                    identifier = "pager1",
                    viewedPages = listOf(
                        ReportingEvent.PageSummaryData.PageView(
                            identifier = "pageA", index = 0, displayTime = 2.seconds
                        )
                    ),
                    pageCount = 2,
                    completed = false
                ),
                ReportingEvent.PageSummaryData(
                    identifier = "pager2",
                    viewedPages = listOf(
                        ReportingEvent.PageSummaryData.PageView(
                            identifier = "pageA", index = 0, displayTime = 3.seconds
                        )
                    ),
                    pageCount = 2,
                    completed = false)
            ),
            tracker.generateSummaryEvents()
        )

        tracker.stopAll(9.seconds)

        assertEquals(
            listOf(
                ReportingEvent.PageSummaryData(
                    identifier = "pager1",
                    viewedPages = listOf(
                        ReportingEvent.PageSummaryData.PageView(
                            identifier = "pageA", index = 0, displayTime = 2.seconds
                        ),
                        ReportingEvent.PageSummaryData.PageView(
                            identifier = "pageB", index = 1, displayTime = 6.seconds
                        )
                    ),
                    pageCount = 2,
                    completed = false
                ),
                ReportingEvent.PageSummaryData(
                    identifier = "pager2",
                    viewedPages = listOf(
                        ReportingEvent.PageSummaryData.PageView(
                            identifier = "pageA", index = 0, displayTime = 3.seconds
                        ),
                        ReportingEvent.PageSummaryData.PageView(
                            identifier = "pageB", index = 1, displayTime = 0.seconds
                        )
                    ),
                    pageCount = 2,
                    completed = false)
            ),
            tracker.generateSummaryEvents()
        )

    }

    @Test
    public fun `onPageView does not re-track the same page if already current`() {
        val pageEvent = ReportingEvent.PageViewData("pager1", "pageA", 0, 3, 3, false)

        tracker.onPageView(pageEvent, 1.seconds)
        tracker.onPageView(pageEvent, 2.seconds) // Same page event
        tracker.stopAll(3.seconds)

        assertEquals(
            listOf(
                ReportingEvent.PageSummaryData(
                    identifier = "pager1",
                    viewedPages = listOf(
                        ReportingEvent.PageSummaryData.PageView(
                            identifier = "pageA", index = 0, displayTime = 2.seconds
                        )
                    ),
                    pageCount = 3,
                    completed = false
                )
            ),
            tracker.generateSummaryEvents()
        )
    }

    @Test
    public fun `generateSummaryEvents returns empty list if no views tracked`() {
        val summaries = tracker.generateSummaryEvents()
        assertTrue(summaries.isEmpty())
    }
}

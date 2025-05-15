package com.urbanairship.android.layout.environment

import com.urbanairship.UALog
import com.urbanairship.android.layout.event.ReportingEvent
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class PagersViewTracker {
    // pager id to tracker
    private val trackers = MutableStateFlow<Map<String, Tracker>>(emptyMap())

    // pager id to last view event
    private val lastPagerPageEvent = MutableStateFlow<Map<String, ReportingEvent.PageViewData>>(emptyMap())

    fun onPageView(
        pageEvent: ReportingEvent.PageViewData,
        currentDisplayTime: Duration
    ) {
        val tracker = trackers.value[pageEvent.identifier] ?: kotlin.run {
            val tracker = Tracker()
            trackers.update { it.toMutableMap().apply { put(pageEvent.identifier, tracker) } }
            tracker
        }

        tracker.start(
            page = ViewedPage(
                identifier = pageEvent.pageIdentifier,
                index = pageEvent.pageIndex
            ),
            currentDisplayTime = currentDisplayTime
        )

        lastPagerPageEvent.update { it.toMutableMap().apply { put(pageEvent.identifier, pageEvent) } }
    }

    fun stop(pagerId: String, currentDisplayTime: Duration) {
        trackers.value[pagerId]?.stop(currentDisplayTime)
    }

    fun stopAll(currentDisplayTime: Duration) {
        trackers.value.values.forEach { it.stop(currentDisplayTime) }
    }

    fun generateSummaryEvents(): List<ReportingEvent.PageSummaryData> {
        return lastPagerPageEvent.value.map { (pagerId, pageEvent) ->
            ReportingEvent.PageSummaryData(
                identifier = pagerId,
                viewedPages = trackers.value[pagerId]?.viewHistory ?: emptyList(),
                pageCount = pageEvent.pageCount,
                completed = pageEvent.completed
            )
        }
    }

    private class Tracker {
        private var currentPage: ViewedPage? = null
        val viewHistory: MutableList<ReportingEvent.PageSummaryData.PageView> = mutableListOf()
        private var currentPageViewStartedTime: Duration? = null

        fun start(page: ViewedPage, currentDisplayTime: Duration) {
            if (currentPage == page) { return }
            stop(currentDisplayTime)
            currentPage = page
            currentPageViewStartedTime = currentDisplayTime
        }

        fun stop(currentDisplayTime: Duration) {
            val startTime = currentPageViewStartedTime ?: return
            val page = currentPage ?: return

            if (currentDisplayTime < startTime) {
                UALog.w { "Current display time is less than start time." }
            }

            viewHistory.add(
                ReportingEvent.PageSummaryData.PageView(
                    identifier = page.identifier,
                    index = page.index,
                    displayTime = (currentDisplayTime - startTime).absoluteValue
                )
            )

            currentPageViewStartedTime = null
            currentPage = null
        }
    }

    private data class ViewedPage(
        val identifier: String,
        val index: Int
    )
}

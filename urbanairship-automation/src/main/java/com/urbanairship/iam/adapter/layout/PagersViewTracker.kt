package com.urbanairship.iam.adapter.layout

import com.urbanairship.android.layout.event.ReportingEvent
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class PagersViewTracker {
    // pager id to tracker
    private val trackers = MutableStateFlow<Map<String, Tracker>>(emptyMap())

    // pager id to last view event
    private val lastPagerPageEvent = MutableStateFlow<Map<String, ReportingEvent.PageViewData>>(emptyMap())

    fun onPageView(
        pageEvent: ReportingEvent.PageViewData,
        currentTime: Long
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
            time = currentTime
        )

        lastPagerPageEvent.update { it.toMutableMap().apply { put(pageEvent.identifier, pageEvent) } }
    }

    fun stopAll(time: Long) {
        trackers.value.values.forEach { it.stop(time) }
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
        private var currentPageViewStartedTime: Long? = null

        fun start(page: ViewedPage, time: Long) {
            if (currentPage == page) { return }
            stop(time)
            currentPage = page
            currentPageViewStartedTime = time
        }

        fun stop(atTime: Long) {
            val startTime = currentPageViewStartedTime ?: return
            val page = currentPage ?: return

            viewHistory.add(
                ReportingEvent.PageSummaryData.PageView(
                    identifier = page.identifier,
                    index = page.index,
                    displayTime = (atTime - startTime).milliseconds
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

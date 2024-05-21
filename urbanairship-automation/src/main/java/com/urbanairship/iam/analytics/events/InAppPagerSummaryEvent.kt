/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics.events

import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

internal class InAppPagerSummaryEvent(
    pagerData: PagerData,
    viewedPages: List<PageViewSummary>
) : InAppEvent {

    private val reportData = PagerSummaryData(
        identifier = pagerData.identifier,
        viewedPages = viewedPages,
        pageCount = pagerData.count,
        completed = pagerData.isCompleted
    )

    override val name: String = "in_app_pager_summary"
    override val data: JsonSerializable = reportData

    private data class PagerSummaryData(
        val identifier: String,
        val viewedPages: List<PageViewSummary>,
        val pageCount: Int,
        val completed: Boolean
    ) : JsonSerializable {
        companion object {
            private const val IDENTIFIER = "pager_identifier"
            private const val VIEWED_PAGES = "viewed_pages"
            private const val PAGE_COUNT = "page_count"
            private const val COMPLETED = "completed"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            IDENTIFIER to identifier,
            VIEWED_PAGES to viewedPages,
            PAGE_COUNT to pageCount,
            COMPLETED to completed
        ).toJsonValue()
    }
}

internal data class PageViewSummary(
    val identifier: String,
    val index: Int,
    val displayTime: Long
) : JsonSerializable {
    companion object {
        private const val IDENTIFIER = "page_identifier"
        private const val INDEX = "page_index"
        private const val DISPLAY_TIME = "display_time"
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        IDENTIFIER to identifier,
        INDEX to index,
        DISPLAY_TIME to displayTime
    ).toJsonValue()
}

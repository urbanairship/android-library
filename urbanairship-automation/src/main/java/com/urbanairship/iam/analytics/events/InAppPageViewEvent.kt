package com.urbanairship.iam.analytics.events

import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

internal class InAppPageViewEvent(
    pagerData: PagerData,
    viewCount: Int
) : InAppEvent {

    private val reportData = PageViewData(
        identifier = pagerData.identifier,
        pageCount = pagerData.count,
        completed = pagerData.isCompleted,
        pageViewCount = viewCount,
        pageIdentifier = pagerData.pageId,
        pageIndex = pagerData.index
    )

    override val name: String = "in_app_page_view"
    override val data: JsonSerializable = reportData

    private data class PageViewData(
        val identifier: String,
        val pageCount: Int,
        val completed: Boolean,
        val pageViewCount: Int,
        val pageIdentifier: String,
        val pageIndex: Int
    ) : JsonSerializable {
        companion object {
            private const val IDENTIFIER = "pager_identifier"
            private const val PAGE_INDEX = "page_index"
            private const val PAGE_COUNT = "page_count"
            private const val PAGE_VIEW_COUNT = "viewed_count"
            private const val PAGE_IDENTIFIER = "page_identifier"
            private const val COMPLETED = "completed"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            IDENTIFIER to identifier,
            PAGE_INDEX to pageIndex,
            PAGE_COUNT to pageCount,
            PAGE_VIEW_COUNT to pageViewCount,
            PAGE_IDENTIFIER to pageIdentifier,
            COMPLETED to completed
        ).toJsonValue()
    }
}

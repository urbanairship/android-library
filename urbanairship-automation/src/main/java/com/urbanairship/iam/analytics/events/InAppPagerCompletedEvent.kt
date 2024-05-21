/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics.events

import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

internal class InAppPagerCompletedEvent(
    info: PagerData
) : InAppEvent {

    private val reportData = PagerCompletedData(info.identifier, info.index, info.count, info.pageId)

    override val name: String = "in_app_pager_completed"
    override val data: JsonSerializable = reportData

    private data class PagerCompletedData(
        val identifier: String,
        val pageIndex: Int,
        val pageCount: Int,
        val pageIdentifier: String
    ) : JsonSerializable {
        companion object {
            private const val IDENTIFIER = "pager_identifier"
            private const val PAGE_INDEX = "page_index"
            private const val PAGE_COUNT = "page_count"
            private const val PAGE_IDENTIFIER = "page_identifier"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            IDENTIFIER to identifier,
            PAGE_INDEX to pageIndex,
            PAGE_COUNT to pageCount,
            PAGE_IDENTIFIER to pageIdentifier
        ).toJsonValue()
    }
}

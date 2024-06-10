/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics.events

import com.urbanairship.analytics.EventType
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

internal class InAppPageSwipeEvent(
    from: PagerData,
    to: PagerData
) : InAppEvent {

    private val reportData = PagerSwipeData(
        identifier = from.identifier,
        toPageIndex = to.index,
        toPageIdentifier = to.pageId,
        fromPageIndex = from.index,
        fromPageIdentifier = from.pageId
    )

    override val eventType: EventType = EventType.IN_APP_PAGE_SWIPE
    override val data: JsonSerializable = reportData

    private data class PagerSwipeData(
        val identifier: String,
        val toPageIndex: Int,
        val toPageIdentifier: String,
        val fromPageIndex: Int,
        val fromPageIdentifier: String
    ) : JsonSerializable {
        companion object {
            private const val IDENTIFIER = "pager_identifier"
            private const val TO_PAGE_INDEX = "to_page_index"
            private const val TO_PAGE_IDENTIFIER = "to_page_identifier"
            private const val FROM_PAGE_INDEX = "from_page_index"
            private const val FROM_PAGE_IDENTIFIER = "from_page_identifier"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            IDENTIFIER to identifier,
            TO_PAGE_INDEX to toPageIndex,
            TO_PAGE_IDENTIFIER to toPageIdentifier,
            FROM_PAGE_INDEX to fromPageIndex,
            FROM_PAGE_IDENTIFIER to fromPageIdentifier
        ).toJsonValue()
    }
}

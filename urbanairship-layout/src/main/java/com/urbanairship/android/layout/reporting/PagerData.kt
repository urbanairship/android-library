/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.reporting

import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

/**
 * Pager state.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class PagerData(
    public val identifier: String,
    public val index: Int,
    public val pageId: String,
    public val count: Int,
    public val history: List<ReportingEvent.PageSummaryData.PageView>,
    public val isCompleted: Boolean
): JsonSerializable {

    private companion object {
        private const val KEY_IDENTIFIER = "identifier"
        private const val KEY_INDEX = "index"
        private const val KEY_PAGE_ID = "page_id"
        private const val KEY_COUNT = "count"
        private const val KEY_HISTORY = "history"
        private const val KEY_IS_COMPLETED = "is_completed"
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        KEY_IDENTIFIER to identifier,
        KEY_INDEX to index,
        KEY_PAGE_ID to pageId,
        KEY_COUNT to count,
        KEY_HISTORY to history,
        KEY_IS_COMPLETED to isCompleted
    ).toJsonValue()
}

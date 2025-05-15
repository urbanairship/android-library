/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.reporting

import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import java.util.Objects

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
)

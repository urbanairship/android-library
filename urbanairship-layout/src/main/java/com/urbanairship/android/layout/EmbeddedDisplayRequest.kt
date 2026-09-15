package com.urbanairship.android.layout

import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.display.DisplayArgs
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.embedded.AirshipEmbeddedInfo
import com.urbanairship.json.JsonMap
import java.util.Objects

/**
 * Wrapper for pending embedded layout data.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class EmbeddedDisplayRequest(
    val embeddedViewId: String,
    val viewInstanceId: String,
    val priority: Int,
    val extras: JsonMap,
    val layoutInfoProvider: () -> LayoutInfo?,
    val displayArgsProvider: () -> DisplayArgs
) {
    /**
     * This request as the info anything choosing between instances reasons about.
     *
     * Resolved once and shared, so a comparator, an eligibility filter, the model's candidates
     * and an app's observer all see the same values — including the priority this was queued
     * with and whatever the layout says about itself. Minting one per consumer is how they
     * drift.
     *
     * Lazy because resolving it calls [layoutInfoProvider]: nothing pays for a description
     * that nothing reads.
     */
    val embeddedInfo: AirshipEmbeddedInfo by lazy {
        val description = layoutInfoProvider()?.contentDescription
        AirshipEmbeddedInfo(
            instanceId = viewInstanceId,
            embeddedId = embeddedViewId,
            priority = priority,
            extras = extras,
            contentDescription = description?.description,
            additionalContext = description?.additionalContext ?: emptyList()
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EmbeddedDisplayRequest
        if (embeddedViewId != other.embeddedViewId) return false
        if (viewInstanceId != other.viewInstanceId) return false
        if (extras != other.extras) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(embeddedViewId, viewInstanceId, extras)
    }
}

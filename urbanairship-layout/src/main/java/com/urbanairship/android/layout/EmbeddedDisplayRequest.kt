package com.urbanairship.android.layout

import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.display.DisplayArgs
import com.urbanairship.android.layout.info.LayoutInfo
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

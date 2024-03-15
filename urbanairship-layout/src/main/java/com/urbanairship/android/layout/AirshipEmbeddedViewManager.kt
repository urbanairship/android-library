package com.urbanairship.android.layout

import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.android.layout.display.DisplayArgs
import com.urbanairship.android.layout.info.LayoutInfo
import kotlinx.coroutines.flow.Flow
import java.util.Objects

/** @hide */
public typealias LayoutInfoProvider = () -> LayoutInfo?

/** @hide */
public typealias DisplayArgsProvider = () -> DisplayArgs

/**
 * Manager interface for Airship Embedded Views.
 */
public interface AirshipEmbeddedViewManager {
    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun addPending(
        embeddedViewId: String,
        viewInstanceId: String,
        layoutInfoProvider: LayoutInfoProvider,
        displayArgsProvider: DisplayArgsProvider
    )

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun addPending(args: DisplayArgs) {
        val payload = args.payload
        val embeddedViewId = (payload.presentation as? EmbeddedPresentation)?.embeddedId ?: run {
            UALog.e { "Failed to add pending embedded view. Required embedded view ID is null!" }
            return
        }
        val viewInstanceId = payload.hashCode().toString()

        addPending(
            embeddedViewId,
            viewInstanceId,
            { payload },
            { args }
        )
    }

    // TODO: maybe don't need these?
    public fun dismiss(embeddedViewId: String)
    public fun dismissAll(embeddedViewId: String)

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun dismiss(embeddedViewId: String, viewInstanceId: String)

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun displayRequests(embeddedViewId: String): Flow<EmbeddedDisplayRequest?>
}

/**
 * Wrapper for pending embedded layout data.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class EmbeddedDisplayRequest(
    val embeddedViewId: String,
    val viewInstanceId: String,
    val layoutInfoProvider: LayoutInfoProvider,
    val displayArgsProvider: DisplayArgsProvider
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EmbeddedDisplayRequest
        if (embeddedViewId != other.embeddedViewId) return false
        if (viewInstanceId != other.viewInstanceId) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(embeddedViewId, viewInstanceId)
    }
}

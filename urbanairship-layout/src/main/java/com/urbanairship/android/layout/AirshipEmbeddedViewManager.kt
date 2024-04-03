/* Copyright Airship and Contributors */

package com.urbanairship.android.layout

import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.android.layout.display.DisplayArgs
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.json.JsonMap
import kotlinx.coroutines.flow.Flow

/** @hide */
public typealias LayoutInfoProvider = () -> LayoutInfo?

/** @hide */
public typealias DisplayArgsProvider = () -> DisplayArgs

/**
 * Manager for Airship Embedded Views.
 */
public interface AirshipEmbeddedViewManager {

    /**
     * Dismisses the currently displayed embedded view for the provided embedded view ID.
     *
     * @param embeddedViewId The embedded view ID.
     */
    public fun dismiss(embeddedViewId: String)

    /**
     * Dismisses the currently displayed embedded view and all pending embedded views for the
     * provided embedded view ID.
     *
     * @param embeddedViewId The embedded view ID.
     */
    public fun dismissAll(embeddedViewId: String)

    /** Returns a flow of all pending embedded view. */
    public fun allPending(): Flow<List<EmbeddedDisplayRequest>>

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun addPending(
        embeddedViewId: String,
        viewInstanceId: String,
        extras: JsonMap = JsonMap.EMPTY_MAP,
        layoutInfoProvider: LayoutInfoProvider,
        displayArgsProvider: DisplayArgsProvider,
    )

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun addPending(args: DisplayArgs, extras: JsonMap) {
        val payload = args.payload
        val embeddedViewId = (payload.presentation as? EmbeddedPresentation)?.embeddedId ?: run {
            UALog.e { "Failed to add pending embedded view. Required embedded view ID is null!" }
            return
        }
        val viewInstanceId = payload.hash.toString()

        addPending(
            embeddedViewId = embeddedViewId,
            viewInstanceId = viewInstanceId,
            extras = extras,
            layoutInfoProvider = { payload },
            displayArgsProvider = { args },
        )
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun dismiss(embeddedViewId: String, viewInstanceId: String)

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun displayRequests(embeddedViewId: String): Flow<EmbeddedDisplayRequest?>
}

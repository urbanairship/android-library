/* Copyright Airship and Contributors */

package com.urbanairship.android.layout

import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.android.layout.display.DisplayArgs
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.embedded.AirshipEmbeddedFilter
import com.urbanairship.embedded.AirshipEmbeddedInfo
import com.urbanairship.embedded.AirshipEmbeddedSelection
import com.urbanairship.embedded.EmbeddedSelectionSession
import com.urbanairship.json.JsonMap
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Manager for Airship Embedded Views.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AirshipEmbeddedViewManager {

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
        priority: Int = 0,
        extras: JsonMap = JsonMap.EMPTY_MAP,
        layoutInfoProvider: () -> LayoutInfo?,
        displayArgsProvider: () -> DisplayArgs,
    )

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun addPending(args: DisplayArgs, priority: Int = 0, extras: JsonMap) {
        val payload = args.payload
        val embeddedViewId = (payload.presentation as? EmbeddedPresentation)?.embeddedId ?: run {
            UALog.e { "Failed to add pending embedded view. Required embedded view ID is null!" }
            return@addPending
        }
        val viewInstanceId = UUID.randomUUID().toString()

        addPending(
            embeddedViewId = embeddedViewId,
            viewInstanceId = viewInstanceId,
            priority = priority,
            extras = extras,
            layoutInfoProvider = { payload },
            displayArgsProvider = { args },
        )
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun dismiss(embeddedViewId: String, viewInstanceId: String)

    /**
     * Records that an instance reached the screen, which is what
     * [AirshipEmbeddedSelection.Priority] keeps displaying until it is dismissed.
     *
     * Called by whatever put the content up, once it is actually up — selecting an instance is
     * not the same as displaying it, and a surface that displays the whole pending list has no
     * single instance to report.
     *
     * @param embeddedViewId The embedded view ID.
     * @param viewInstanceId The instance now on screen.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun recordDisplayed(embeddedViewId: String, viewInstanceId: String)

    /**
     * The display requests for an embedded view.
     *
     * @param embeddedViewId The embedded view ID.
     * @param selection Which pending instance to display, and in what order.
     * @param filter Which pending instances are eligible at all, applied before [selection].
     * @param session State an [AirshipEmbeddedSelection.ByAi] selection keeps across
     * collections, so a view that detaches and reattaches neither blanks nor re-ranks. Held by
     * the caller for as long as the view and its config live. Null gives each collection its
     * own, which is all a selection that decides synchronously needs.
     * @param scope The scope the returned flow is shared in.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun displayRequests(
        embeddedViewId: String,
        selection: AirshipEmbeddedSelection = AirshipEmbeddedSelection.Priority,
        filter: AirshipEmbeddedFilter? = null,
        session: EmbeddedSelectionSession? = null,
        scope: CoroutineScope
    ): Flow<EmbeddedDisplayRequestResult>

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Deprecated("Use displayRequests with AirshipEmbeddedSelection instead.")
    public fun displayRequests(
        embeddedViewId: String,
        comparator: Comparator<AirshipEmbeddedInfo>?,
        scope: CoroutineScope
    ): Flow<EmbeddedDisplayRequestResult> {
        return displayRequests(
            embeddedViewId = embeddedViewId,
            selection = if (comparator != null) AirshipEmbeddedSelection.ByComparator(comparator) else AirshipEmbeddedSelection.Priority,
            filter = null,
            session = null,
            scope = scope
        )
    }
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class EmbeddedDisplayRequestResult(public val next: EmbeddedDisplayRequest?, public val list: List<EmbeddedDisplayRequest>)

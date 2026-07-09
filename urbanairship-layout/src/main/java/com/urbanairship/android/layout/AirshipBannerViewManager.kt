/* Copyright Airship and Contributors */

package com.urbanairship.android.layout

import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.display.DisplayArgs
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.json.JsonMap
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Manager for Airship Banner Views.
 *
 * Banners are global overlays, so pending banners are observed as a single stream by the
 * banner host, rather than being keyed by an author-provided ID like embedded views.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AirshipBannerViewManager {

    /** Dismisses the currently displayed banner and all pending banners. */
    public fun dismissAll()

    /** Returns a flow of all pending banners. */
    public fun allPending(): Flow<List<BannerDisplayRequest>>

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun addPending(args: DisplayArgs, priority: Int = 0, extras: JsonMap = JsonMap.EMPTY_MAP) {
        val payload = args.payload
        val viewInstanceId = UUID.randomUUID().toString()

        addPending(
            viewInstanceId = viewInstanceId,
            priority = priority,
            extras = extras,
            layoutInfoProvider = { payload },
            displayArgsProvider = { args },
        )
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun addPending(
        viewInstanceId: String,
        priority: Int = 0,
        extras: JsonMap = JsonMap.EMPTY_MAP,
        layoutInfoProvider: () -> LayoutInfo?,
        displayArgsProvider: () -> DisplayArgs,
    )

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun dismiss(viewInstanceId: String)

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun displayRequests(scope: CoroutineScope): Flow<BannerDisplayRequestResult>
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class BannerDisplayRequestResult(
    public val next: BannerDisplayRequest?,
    public val list: List<BannerDisplayRequest>
)

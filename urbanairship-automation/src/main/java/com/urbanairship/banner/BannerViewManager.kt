/* Copyright Airship and Contributors */

package com.urbanairship.banner

import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.android.layout.AirshipBannerViewManager
import com.urbanairship.android.layout.BannerDisplayRequest
import com.urbanairship.android.layout.BannerDisplayRequestResult
import com.urbanairship.android.layout.display.DisplayArgs
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.json.JsonMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object BannerViewManager : AirshipBannerViewManager {

    private val pendingFlow = MutableStateFlow<List<BannerDisplayRequest>>(emptyList())

    private var lastViewed: String? = null
    private val lastViewedLock = ReentrantLock()

    public override fun addPending(
        viewInstanceId: String,
        priority: Int,
        extras: JsonMap,
        layoutInfoProvider: () -> LayoutInfo?,
        displayArgsProvider: () -> DisplayArgs
    ) {
        val request = BannerDisplayRequest(
            viewInstanceId = viewInstanceId,
            priority = priority,
            extras = extras,
            layoutInfoProvider = layoutInfoProvider,
            displayArgsProvider = displayArgsProvider
        )

        pendingFlow.value += request

        UALog.v { "Banner view has ${pendingFlow.value.size} pending" }
    }

    override fun dismissAll() {
        pendingFlow.value = emptyList()
        UALog.v { "Banner view has ${pendingFlow.value.size} pending" }
    }

    override fun dismiss(viewInstanceId: String) {
        // Remove the request for the given view instance ID from the list of pending requests
        pendingFlow.value = pendingFlow.value.filterNot { it.viewInstanceId == viewInstanceId }
        UALog.v { "Banner view has ${pendingFlow.value.size} pending" }
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun allPending(): Flow<List<BannerDisplayRequest>> = pendingFlow

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun displayRequests(scope: CoroutineScope): Flow<BannerDisplayRequestResult> {

        // This assumes displayRequests will be subscribed only when its actually
        // visible/attached to window. The first thing that subscribes will cause any
        // subsequent calls to this method to get the same BannerDisplayRequest until
        // it is no longer in the listing.

        return pendingFlow
            .map { list ->
                val current = lastViewedLock.withLock {
                    lastViewed?.let { lastId ->
                        list.find { it.viewInstanceId == lastId }
                    } ?: list.minByOrNull { it.priority }?.also {
                        lastViewed = it.viewInstanceId
                    }
                }
                BannerDisplayRequestResult(next = current, list = list)
            }
            .distinctUntilChanged()
            .shareIn(scope, replay = 1, started = WhileSubscribed())
    }
}

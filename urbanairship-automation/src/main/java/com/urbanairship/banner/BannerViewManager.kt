/* Copyright Airship and Contributors */

package com.urbanairship.banner

import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.android.layout.AirshipBannerViewManager
import com.urbanairship.android.layout.BannerDisplayRequest
import com.urbanairship.android.layout.BannerDisplayRequestResult
import com.urbanairship.android.layout.display.DisplayArgs
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.android.layout.ui.BannerViewModelStores
import com.urbanairship.json.JsonMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object BannerViewManager : AirshipBannerViewManager {

    private val pendingFlow = MutableStateFlow<List<BannerDisplayRequest>>(emptyList())

    private var lastViewed: String? = null
    private val lastViewedLock = ReentrantLock()

    override fun addPending(
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

        pendingFlow.update { it + request }

        UALog.v { "Banner view has ${pendingFlow.value.size} pending" }
    }

    override fun dismissAll() {
        val dropped = pendingFlow.getAndUpdate { emptyList() }
        lastViewedLock.withLock { lastViewed = null }
        dropped.forEach(::resolveDropped)

        UALog.v { "Banner view has ${pendingFlow.value.size} pending" }
    }

    override fun dismiss(viewInstanceId: String) {
        // Remove the request for the given view instance ID from the list of pending requests
        var removed: BannerDisplayRequest? = null
        pendingFlow.update { list ->
            removed = list.find { it.viewInstanceId == viewInstanceId }
            list.filterNot { it.viewInstanceId == viewInstanceId }
        }

        val request = removed
        if (request == null) {
            UALog.d { "No pending banner to dismiss for view instance: $viewInstanceId" }
            return
        }

        // Displayed banners resolve their own display requests via the display listener when
        // they're dismissed. A request that was never displayed won't, so resolve it here.
        val wasDisplayed = lastViewedLock.withLock { lastViewed == viewInstanceId }
        if (!wasDisplayed) {
            resolveDropped(request)
        }

        UALog.v { "Banner view has ${pendingFlow.value.size} pending" }
    }

    /**
     * Resolves a display request that was dropped from the queue without a reported dismiss,
     * cancelling it via its display listener so that the suspended display coroutine behind the
     * request is resumed, and clears any view model store entry for the instance.
     */
    private fun resolveDropped(request: BannerDisplayRequest) {
        try {
            request.displayArgsProvider().listener.onDismiss(cancel = true)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to resolve dropped banner display request: ${request.viewInstanceId}" }
        }
        BannerViewModelStores.clear(request.viewInstanceId)
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

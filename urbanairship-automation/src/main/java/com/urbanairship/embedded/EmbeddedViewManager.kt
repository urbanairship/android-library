/* Copyright Airship and Contributors */

package com.urbanairship.embedded

import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.android.layout.AirshipEmbeddedViewManager
import com.urbanairship.android.layout.EmbeddedDisplayRequest
import com.urbanairship.android.layout.EmbeddedDisplayRequestResult
import com.urbanairship.android.layout.display.DisplayArgs
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.json.JsonMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object EmbeddedViewManager : AirshipEmbeddedViewManager {

    private val pending: MutableMap<String, List<EmbeddedDisplayRequest>> = mutableMapOf()
    private val viewsFlow = MutableStateFlow<Map<String, List<EmbeddedDisplayRequest>>>(emptyMap())

    private val lastViewed: MutableMap<String, String> = mutableMapOf()
    private val lastViewedLock =  ReentrantLock()

    public override fun addPending(
        embeddedViewId: String,
        viewInstanceId: String,
        priority: Int,
        extras: JsonMap,
        layoutInfoProvider: () -> LayoutInfo?,
        displayArgsProvider: () -> DisplayArgs
    ) {
        val pendingForView = pending[embeddedViewId]

        val request = EmbeddedDisplayRequest(
            embeddedViewId = embeddedViewId,
            viewInstanceId = viewInstanceId,
            priority = priority,
            extras = extras,
            layoutInfoProvider = layoutInfoProvider,
            displayArgsProvider = displayArgsProvider
        )

        if (pendingForView.isNullOrEmpty()) {
            pending[embeddedViewId] = listOf(request)
        } else {
            pending[embeddedViewId] = pendingForView + request
        }

        UALog.v { "Embedded view '$embeddedViewId' has ${pending[embeddedViewId]?.size} pending" }

        viewsFlow.value = pending.toMap()
    }

    override fun dismissAll(embeddedViewId: String) {
        pending[embeddedViewId] = emptyList()
        UALog.v { "Embedded view '$embeddedViewId' has ${pending[embeddedViewId]?.size} pending" }

        viewsFlow.value = pending.toMap()
    }

    override fun dismiss(embeddedViewId: String, viewInstanceId: String) {
        val pendingForView = pending[embeddedViewId] ?: return

        // Remove the request for the given view instance ID from the list of pending requests
        pending[embeddedViewId] = pendingForView.filterNot { it.viewInstanceId == viewInstanceId }
        UALog.v { "Embedded view '$embeddedViewId' has ${pending[embeddedViewId]?.size} pending" }

        viewsFlow.value = pending.toMap()
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun allPending(): Flow<List<EmbeddedDisplayRequest>> {
        @OptIn(ExperimentalCoroutinesApi::class)
        return viewsFlow.flatMapConcat {
            flowOf(it.values.flatten())
        }
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun displayRequests(
        embeddedViewId: String,
        comparator: Comparator<AirshipEmbeddedInfo>?,
        scope: CoroutineScope,
    ): Flow<EmbeddedDisplayRequestResult> {

        // This assumes displayRequests will be subscribed only when its actually
        // visible/attached to window. The first thing that subscribes will cause any
        // subsequent calls to this method to get the same EmbeddedDisplayRequest until
        // it is no longer in the listing.

        return viewsFlow
            .map { list ->
                val sorted = if (comparator != null) {
                    list[embeddedViewId]
                        // Map the list to a list of pairs, so we can sort by the embedded info
                        ?.map { request ->
                            val info = AirshipEmbeddedInfo(
                                instanceId = request.viewInstanceId,
                                embeddedId = request.embeddedViewId,
                                extras = request.extras
                            )
                            Pair(info, request)
                        }
                       ?.sortedWith { a, b -> comparator.compare(a.first, b.first) }
                       // Map the list back to just the request, with the sort order applied
                       ?.map { it.second }
                } else {
                    list[embeddedViewId]
                }

                sorted.orEmpty()
            }
            .map { list ->
                if (comparator != null) {
                    EmbeddedDisplayRequestResult(next = list.firstOrNull(), list = list)
                } else {
                    val current = lastViewedLock.withLock {
                        lastViewed[embeddedViewId]?.let { lastId ->
                            list.find { it.viewInstanceId == lastId }
                        } ?: list.minByOrNull { it.priority }?.also {
                            lastViewed[embeddedViewId] = it.viewInstanceId
                        }
                    }
                    EmbeddedDisplayRequestResult(next = current, list =  list)
                }
            }
            .distinctUntilChanged()
            .shareIn(scope, replay = 1, started = WhileSubscribed())
    }
}

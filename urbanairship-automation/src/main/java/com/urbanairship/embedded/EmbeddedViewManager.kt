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
        selection: AirshipEmbeddedSelection,
        scope: CoroutineScope,
    ): Flow<EmbeddedDisplayRequestResult> {

        // This assumes displayRequests will be subscribed only when its actually
        // visible/attached to window. The first thing that subscribes will cause any
        // subsequent calls to this method to get the same EmbeddedDisplayRequest until
        // it is no longer in the listing.

        return viewsFlow
            .map { map ->
                val pendingList = map[embeddedViewId].orEmpty()

                when (selection) {
                    is AirshipEmbeddedSelection.ByComparator -> {
                        val sorted = pendingList
                            .map { request -> request.embeddedInfo() to request }
                            .sortedWith { a, b -> selection.comparator.compare(a.first, b.first) }
                            .map { it.second }
                        EmbeddedDisplayRequestResult(next = sorted.firstOrNull(), list = sorted)
                    }
                    is AirshipEmbeddedSelection.ByInstanceId -> {
                        val match = pendingList.find { it.viewInstanceId == selection.instanceId }
                        EmbeddedDisplayRequestResult(next = match, list = pendingList)
                    }
                    AirshipEmbeddedSelection.Priority -> {
                        val current = lastViewedLock.withLock {
                            lastViewed[embeddedViewId]?.let { lastId ->
                                pendingList.find { it.viewInstanceId == lastId }
                            } ?: pendingList.minByOrNull { it.priority }?.also {
                                lastViewed[embeddedViewId] = it.viewInstanceId
                            }
                        }
                        EmbeddedDisplayRequestResult(next = current, list = pendingList)
                    }
                }
            }
            .distinctUntilChanged()
            .shareIn(scope, replay = 1, started = WhileSubscribed())
    }
}

/**
 * The pending request as the info a selection reasons about.
 *
 * Deliberately does not touch [EmbeddedDisplayRequest.layoutInfoProvider]: this runs for every
 * candidate on every emission of the pending list, and the payload is behind a provider so that
 * it isn't resolved on that path. What a layout says about itself is read only where a
 * selection actually needs it — see [describing].
 *
 * @return The info.
 */
internal fun EmbeddedDisplayRequest.embeddedInfo(): AirshipEmbeddedInfo = AirshipEmbeddedInfo(
    instanceId = viewInstanceId,
    embeddedId = embeddedViewId,
    // From the request rather than the type's default, so a comparator that sorts on priority
    // sees the real value.
    priority = priority,
    extras = extras
)

/**
 * The same info with the layout's `content_description` folded in, for a selection that reasons
 * about what the content *is* rather than only about priority.
 *
 * Resolves the layout payload, so call it once per selection rather than per emission.
 *
 * @return The info, described as far as the layout describes itself.
 */
internal fun EmbeddedDisplayRequest.describing(): AirshipEmbeddedInfo {
    val description = layoutInfoProvider()?.contentDescription ?: return embeddedInfo()
    return AirshipEmbeddedInfo(
        instanceId = viewInstanceId,
        embeddedId = embeddedViewId,
        priority = priority,
        extras = extras,
        contentDescription = description.description,
        additionalContext = description.additionalContext
    )
}

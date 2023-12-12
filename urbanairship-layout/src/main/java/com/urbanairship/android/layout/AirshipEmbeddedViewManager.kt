package com.urbanairship.android.layout

import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.android.layout.display.DisplayArgs
import com.urbanairship.android.layout.info.LayoutInfo
import java.util.Objects
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

public interface AirshipEmbeddedViewManager {
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun addPending(
        embeddedViewId: String,
        viewInstanceId: String,
        layoutInfoProvider: LayoutInfoProvider,
        displayArgsProvider: DisplayArgsProvider
    )

    // TODO: maybe don't need these?
    public fun dismiss(embeddedViewId: String)
    public fun dismissAll(embeddedViewId: String)

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun dismiss(embeddedViewId: String, viewInstanceId: String)

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun displayRequests(embeddedViewId: String): Flow<EmbeddedDisplayRequest?>
}

/** Wrapper for pending embedded layout data. */
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

internal typealias LayoutInfoProvider = () -> LayoutInfo?
internal typealias DisplayArgsProvider = () -> DisplayArgs

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object DefaultEmbeddedViewManager : AirshipEmbeddedViewManager {

    private val pending: MutableMap<String, List<EmbeddedDisplayRequest>> = mutableMapOf()

    private val viewsFlow = MutableStateFlow<Map<String, List<EmbeddedDisplayRequest>>>(emptyMap())

    public override fun addPending(
        embeddedViewId: String,
        viewInstanceId: String,
        layoutInfoProvider: LayoutInfoProvider,
        displayArgsProvider: DisplayArgsProvider
    ) {
        val pendingForView = pending[embeddedViewId]

        val request = EmbeddedDisplayRequest(
            embeddedViewId = embeddedViewId,
            viewInstanceId = viewInstanceId,
            layoutInfoProvider = layoutInfoProvider,
            displayArgsProvider = displayArgsProvider
        )

        if (pendingForView.isNullOrEmpty()) {
            pending[embeddedViewId] = listOf(request)
        } else {
            pending[embeddedViewId] = pendingForView + request
        }

        UALog.v { "Embedded view '$embeddedViewId' has ${pending[embeddedViewId]?.size} pending" }

        viewsFlow.tryEmit(pending.toMap())
    }

    override fun dismiss(embeddedViewId: String) {
        val pendingForView = pending[embeddedViewId] ?: return

        // Pop the first request off the list of pending requests
        pending[embeddedViewId] = pendingForView.drop(1)

        UALog.v { "Embedded view '$embeddedViewId' has ${pending[embeddedViewId]?.size} pending" }

        viewsFlow.tryEmit(pending.toMap())
    }

    override fun dismissAll(embeddedViewId: String) {
        pending[embeddedViewId] = emptyList()
        UALog.v { "Embedded view '$embeddedViewId' has ${pending[embeddedViewId]?.size} pending" }

        viewsFlow.tryEmit(pending.toMap())
    }

    override fun dismiss(embeddedViewId: String, viewInstanceId: String) {
        val pendingForView = pending[embeddedViewId] ?: return

        // Remove the request for the given view instance ID from the list of pending requests
        pending[embeddedViewId] = pendingForView.filterNot { it.viewInstanceId == viewInstanceId }
        UALog.v { "Embedded view '$embeddedViewId' has ${pending[embeddedViewId]?.size} pending" }

        viewsFlow.tryEmit(pending.toMap())
    }

    override fun displayRequests(embeddedViewId: String): Flow<EmbeddedDisplayRequest?> {
        return viewsFlow.map { it[embeddedViewId]?.firstOrNull() }.distinctUntilChanged()
    }
}

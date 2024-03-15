package com.urbanairship.embedded

import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.android.layout.EmbeddedPresentation
import com.urbanairship.android.layout.display.DisplayArgs
import com.urbanairship.android.layout.AirshipEmbeddedViewManager
import com.urbanairship.android.layout.DisplayArgsProvider
import com.urbanairship.android.layout.EmbeddedDisplayRequest
import com.urbanairship.android.layout.LayoutInfoProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** @hide */
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

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun dismiss(embeddedViewId: String, viewInstanceId: String) {
        val pendingForView = pending[embeddedViewId] ?: return

        // Remove the request for the given view instance ID from the list of pending requests
        pending[embeddedViewId] = pendingForView.filterNot { it.viewInstanceId == viewInstanceId }
        UALog.v { "Embedded view '$embeddedViewId' has ${pending[embeddedViewId]?.size} pending" }

        viewsFlow.tryEmit(pending.toMap())
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun displayRequests(embeddedViewId: String): Flow<EmbeddedDisplayRequest?> {
        return viewsFlow.map { it[embeddedViewId]?.firstOrNull() }.distinctUntilChanged()
    }
}

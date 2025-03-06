package com.urbanairship.automation.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.urbanairship.android.layout.AirshipEmbeddedViewManager
import com.urbanairship.android.layout.EmbeddedDisplayRequest
import com.urbanairship.android.layout.ui.EmbeddedLayout
import com.urbanairship.embedded.AirshipEmbeddedInfo
import com.urbanairship.embedded.EmbeddedViewManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Creates a [AirshipEmbeddedViewGroupState] that can be used to manage the state of an embedded
 * view group.
 *
 * @param embeddedId the embedded ID.
 * @param comparator optional `Comparator` used to sort available embedded contents.
 *
 * @return a new [AirshipEmbeddedViewGroupState] instance.
 */
@Composable
public fun rememberAirshipEmbeddedViewGroupState(
    embeddedId: String,
    comparator: Comparator<AirshipEmbeddedInfo>? = null
): AirshipEmbeddedViewGroupState {
    return rememberAirshipEmbeddedViewGroupState(embeddedId, comparator, EmbeddedViewManager)
}

/**
 * State holder for [AirshipEmbeddedViewGroup] content.
 *
 * @param embeddedId the embedded ID.
 */
@Stable
public class AirshipEmbeddedViewGroupState(
    public val embeddedId: String
) {
    internal var displayRequests by mutableStateOf<List<EmbeddedDisplayRequest>>(emptyList())

    /** Embedded view items, containing the [AirshipEmbeddedInfo] and content to display. */
    public val items: State<List<EmbeddedViewItem>> = derivedStateOf(policy = structuralEqualityPolicy()) {
        displayRequests.map { request -> EmbeddedViewItem(request = request) }
    }
}

@Composable
internal fun rememberAirshipEmbeddedViewGroupState(
    embeddedId: String,
    comparator: Comparator<AirshipEmbeddedInfo>?,
    embeddedViewManager: AirshipEmbeddedViewManager,
): AirshipEmbeddedViewGroupState {
    val state = remember { AirshipEmbeddedViewGroupState(embeddedId) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(embeddedId, comparator) {
        withContext(Dispatchers.Default) {
            embeddedViewManager.displayRequests(embeddedId, comparator, scope)
                .map { it.list }
                .distinctUntilChanged()
                .collect { state.displayRequests = it }
        }
    }

    return state
}


/**
 * An embedded view item, containing the [AirshipEmbeddedInfo] and the content to display.
 */
@Immutable
public data class EmbeddedViewItem internal constructor(
    private val request: EmbeddedDisplayRequest
) {
    /** The [AirshipEmbeddedInfo] for this embedded content. */
    public val info: AirshipEmbeddedInfo = AirshipEmbeddedInfo(
        embeddedId = request.embeddedViewId,
        instanceId = request.viewInstanceId,
        priority = request.priority,
        extras = request.extras,
    )

    /** The content to display for this embedded view item. */
    @Composable
    public fun content() {
        val layout = EmbeddedLayout(
            context = LocalContext.current,
            embeddedViewId = request.embeddedViewId,
            viewInstanceId = request.viewInstanceId,
            args = request.displayArgsProvider.invoke(),
            embeddedViewManager = EmbeddedViewManager
        )

        EmbeddedViewWrapper(
            embeddedId = request.embeddedViewId,
            embeddedLayout = layout,
            embeddedSize = layout.getPlacement()?.size?.toEmbeddedSize(),
            // Consumers provide their own placeholder, if desired.
            placeholder = null,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

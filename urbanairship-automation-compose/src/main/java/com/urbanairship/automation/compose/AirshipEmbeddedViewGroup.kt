/* Copyright Airship and Contributors */

package com.urbanairship.automation.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.urbanairship.android.layout.EmbeddedDisplayRequest
import com.urbanairship.android.layout.ui.EmbeddedLayout
import com.urbanairship.embedded.AirshipEmbeddedInfo
import com.urbanairship.embedded.EmbeddedViewManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * A container that allows all embedded content for the given [embeddedId]
 * to be displayed using the provided [content] composable.
 *
 * @param embeddedId The embedded ID.
 * @param modifier The modifier to be applied to the layout.
 * @param comparator Optional [Comparator] used to sort available embedded view content.
 * @param content The `Composable` that will display the list of embedded view content.
 */
@Composable
public fun AirshipEmbeddedViewGroup(
    embeddedId: String,
    modifier: Modifier = Modifier,
    comparator: Comparator<AirshipEmbeddedInfo>? = null,
    content: @Composable BoxScope.(embeddedViews: List<EmbeddedViewItem>) -> Unit
) {
    val scope = rememberCoroutineScope()

    val displayRequests = EmbeddedViewManager.displayRequests(embeddedId, comparator, scope)
        .map { it.list }
        .distinctUntilChanged()
        .collectAsStateWithLifecycle(emptyList())

    val items: State<List<EmbeddedViewItem>> = derivedStateOf(policy = structuralEqualityPolicy()) {
        displayRequests.value.map { request -> EmbeddedViewItem(request = request) }
    }

    Box(modifier) {
        content(items.value)
    }
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

@Preview
@Composable
private fun AirshipEmbeddedViewPagerPreview() {
    AirshipEmbeddedViewGroup(
        embeddedId = "embeddedId",
        modifier = Modifier.fillMaxSize()
    ) { views ->
        views.first().content()
    }
}

/* Copyright Airship and Contributors */

package com.urbanairship.automation.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    AirshipEmbeddedViewGroup(
        modifier = modifier,
        state = rememberAirshipEmbeddedViewGroupState(embeddedId, comparator),
        content = content
    )
}

/**
 * A container that allows all embedded content for the `embeddedId` defined on the given
 * [AirshipEmbeddedViewGroupState] instance.
 *
 * This composable may be useful when access to the embedded view group state is needed outside of
 * the `AirshipEmbeddedViewGroup` composable, embedded view group state should be hoisted, and for
 * advanced custom logic that depends on the availability of embedded view content.
 *
 * When included inside of a lazy composable or scrolling list, state should be hoisted up, above
 * the lazy composable or scrolling list, in order to ensure that the embedded view group state is
 * maintained across recompositions.
 *
 * @param state The [AirshipEmbeddedViewGroupState] to be used by this embedded view group.
 * @param modifier The modifier to be applied to the layout.
 * @param content The `Composable` that will display the list of embedded view content.
 */
@Composable
public fun AirshipEmbeddedViewGroup(
    state: AirshipEmbeddedViewGroupState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(embeddedViews: List<EmbeddedViewItem>) -> Unit
) {
    Box(modifier) {
        content(state.items.value)
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

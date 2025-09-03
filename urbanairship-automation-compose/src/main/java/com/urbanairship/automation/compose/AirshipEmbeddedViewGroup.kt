/* Copyright Airship and Contributors */

package com.urbanairship.automation.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.urbanairship.embedded.AirshipEmbeddedInfo

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

/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.property.Background
import com.urbanairship.android.layout.property.Background.MediaBackground.VideoMediaBackground

/**
 * A real [MediaModel] for every playable `backgrounds` layer, keyed by the layer it came from.
 *
 * Video can't be painted onto a canvas the way a color, gradient or image layer is -- it needs a
 * real, attached view to play at all -- so a host with one of these layers renders it as an actual
 * child [com.urbanairship.android.layout.view.MediaView], and tells its
 * [com.urbanairship.android.layout.widget.BackgroundsPainter] to skip the layer. The layer
 * already carries its own [MediaInfo], parsed with the rest of the scene.
 *
 * @param layers the host's own `backgrounds`, already unwrapped from any `parallax` pairing.
 * @param pagerState the enclosing pager's state, or `null` outside one.
 * @param properties the host's own -- never a synthesized one. Its `pagerPageId` is what scopes
 *   the layer to a page for pause/rewind-when-offscreen, and a host nested in a pager page already
 *   carries that page's id (see `ModelFactory`), so passing it through is the whole of what the
 *   lifecycle needs. A host that is not in a pager carries `null`, which
 *   [MediaModel.isOnCurrentPage] reads as always current.
 */
internal fun videoBackgroundModels(
    layers: List<Background>?,
    pagerState: SharedState<State.Pager>?,
    environment: ModelEnvironment,
    properties: ModelProperties,
    holdsStoryOnEveryPage: Boolean = false
): Map<VideoMediaBackground, MediaModel> = layers.orEmpty()
    .filterIsInstance<VideoMediaBackground>()
    .associateWith { layer ->
        MediaModel(
            viewInfo = layer.mediaInfo,
            pagerState = pagerState,
            videoState = environment.layoutState.video,
            environment = environment,
            properties = properties,
            holdsStoryOnEveryPage = holdsStoryOnEveryPage
        )
    }

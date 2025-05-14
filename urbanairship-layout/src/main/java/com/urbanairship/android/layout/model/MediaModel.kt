/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.MediaInfo
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.MediaType
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.view.MediaView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal class MediaModel(
    viewInfo: MediaInfo,
    val pagerState: SharedState<State.Pager>?,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<MediaView, MediaInfo, MediaModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    val mediaViewId: Int = View.generateViewId()

    private val isPlayableMedia: Boolean =
        viewInfo.mediaType.let {it == MediaType.VIDEO || it == MediaType.YOUTUBE || it == MediaType.VIMEO }

    interface Listener : BaseModel.Listener {
        fun onPause()
        fun onResume()
    }

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = MediaView(context, this, viewEnvironment, itemProperties).apply {
        id = viewId
    }

    override fun onViewAttached(view: MediaView) {
        if (viewInfo.eventHandlers.hasTapHandler()) {
            viewScope.launch {
                view.taps().collect { handleViewEvent(EventHandler.Type.TAP) }
            }
        }

        modelScope.launch {
            if (isPlayableMedia) {
                pagerState?.changes?.distinctUntilChanged { old, new -> old.isStoryPaused == new.isStoryPaused }
                    ?.collect {
                        if (it.isStoryPaused) {
                            listener?.onPause()
                        } else {
                            listener?.onResume()
                        }
                    }
            }
        }
    }
}

/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.MediaInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.MediaFit
import com.urbanairship.android.layout.property.MediaType
import com.urbanairship.android.layout.property.Position
import com.urbanairship.android.layout.property.Video
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.view.MediaView
import kotlinx.coroutines.launch

internal class MediaModel(
    val url: String,
    val mediaType: MediaType,
    val mediaFit: MediaFit,
    val position: Position,
    val contentDescription: String? = null,
    val video: Video?,
    val pagerState: SharedState<State.Pager>?,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<MediaView, BaseModel.Listener>(
    viewType = ViewType.MEDIA,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment,
    properties = properties
) {
    constructor(info: MediaInfo, pagerState: SharedState<State.Pager>?, env: ModelEnvironment, props: ModelProperties) : this(
        url = info.url,
        mediaType = info.mediaType,
        mediaFit = info.mediaFit,
        position = info.position,
        contentDescription = info.contentDescription,
        video = info.video,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        pagerState = pagerState,
        environment = env,
        properties = props
    )

    override fun onCreateView(context: Context, viewEnvironment: ViewEnvironment) =
        MediaView(context, this, viewEnvironment).apply {
            id = viewId
        }

    override fun onViewAttached(view: MediaView) {
        if (eventHandlers.hasTapHandler()) {
            viewScope.launch {
                view.taps().collect { handleViewEvent(EventHandler.Type.TAP) }
            }
        }
    }
}

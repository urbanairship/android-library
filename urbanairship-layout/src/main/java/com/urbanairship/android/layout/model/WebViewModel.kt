/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.os.Bundle
import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.info.WebViewInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.view.WebViewView
import kotlinx.coroutines.launch

internal class WebViewModel(
    val url: String,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    environment: ModelEnvironment
) : BaseModel<WebViewView, BaseModel.Listener>(
    viewType = ViewType.WEB_VIEW,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment
) {
    constructor(info: WebViewInfo, env: ModelEnvironment) : this(
        url = info.url,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        environment = env
    )

    var savedState: Bundle? = null

    override fun onCreateView(context: Context, viewEnvironment: ViewEnvironment) =
        WebViewView(context, this, viewEnvironment).apply {
            id = viewId
        }

    override fun onViewAttached(view: WebViewView) {
        if (eventHandlers.hasTapHandler()) {
            viewScope.launch {
                view.taps().collect { handleViewEvent(EventHandler.Type.TAP) }
            }
        }
    }

    fun onClose() {
        report(
            ReportingEvent.DismissFromOutside(environment.displayTimer.time),
            layoutState.reportingContext()
        )
        broadcast(LayoutEvent.Finish)
    }
}

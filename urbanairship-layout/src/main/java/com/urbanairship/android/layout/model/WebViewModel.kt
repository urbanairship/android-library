/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.os.Bundle
import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.event.WebViewEvent
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.info.WebViewInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.LayoutData

internal class WebViewModel(
    val url: String,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    environment: ModelEnvironment
) : BaseModel(
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
        private set

    fun onClose() {
        bubbleEvent(WebViewEvent.Close(), LayoutData.empty())
    }

    fun saveState(bundle: Bundle) {
        savedState = bundle
    }
}

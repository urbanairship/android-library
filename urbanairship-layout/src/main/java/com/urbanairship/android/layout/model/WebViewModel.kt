/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.os.Bundle
import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.WebViewInfo
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.view.WebViewView
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch

internal class WebViewModel(
    viewInfo: WebViewInfo,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<WebViewView, WebViewInfo, BaseModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    var savedState: Bundle? = null

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = WebViewView(context, this, viewEnvironment).apply {
        id = viewId
    }

    override fun onViewAttached(view: WebViewView) {
        if (viewInfo.eventHandlers.hasTapHandler()) {
            viewScope.launch {
                view.taps().collect { handleViewEvent(EventHandler.Type.TAP) }
            }
        }
    }

    fun onClose() {
        report(
            event = ReportingEvent.Dismiss(
                data = ReportingEvent.DismissData.UserDismissed,
                displayTime = environment.displayTimer.time.milliseconds,
                context = layoutState.reportingContext()
            )
        )
        broadcast(LayoutEvent.Finish())
    }
}

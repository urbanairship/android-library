/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.PagerControllerInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.ViewType
import kotlinx.coroutines.launch

/**
 * Controller that manages communication between Pager and PagerIndicator children.
 */
internal class PagerController(
    val view: AnyModel,
    val identifier: String,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    private val pagerState: SharedState<State.Pager>,
    environment: ModelEnvironment
) : BaseModel<View, BaseModel.Listener>(
    viewType = ViewType.PAGER_CONTROLLER,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment
) {
    constructor(
        info: PagerControllerInfo,
        view: AnyModel,
        pagerState: SharedState<State.Pager>,
        env: ModelEnvironment
    ) : this(
        view = view,
        identifier = info.identifier,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        pagerState = pagerState,
        environment = env
    )

    init {
        modelScope.launch {
            pagerState.changes.collect { state ->
                reportPageView(state)
            }
        }
    }

    override fun onCreateView(context: Context, viewEnvironment: ViewEnvironment) =
        view.createView(context, viewEnvironment)

    private fun reportPageView(pagerState: State.Pager) {
        val pagerContext = pagerState.reportingContext()
        report(
            ReportingEvent.PageView(pagerContext, environment.displayTimer.time),
            layoutState.reportingContext(pagerContext = pagerContext)
        )
    }
}

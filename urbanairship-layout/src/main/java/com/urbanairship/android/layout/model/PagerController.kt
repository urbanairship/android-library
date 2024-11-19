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
import com.urbanairship.android.layout.reporting.PagerData
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Controller that manages communication between Pager and PagerIndicator children.
 */
internal class PagerController(
    viewInfo: PagerControllerInfo,
    val view: AnyModel,
    private val pagerState: SharedState<State.Pager>,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<View, PagerControllerInfo, BaseModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    init {
        modelScope.launch {
            pagerState.changes.map { it.reportingContext() }.distinctUntilChanged()
                .collect(::reportPageView)
        }
    }

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = view.createView(context, viewEnvironment, itemProperties)

    private fun reportPageView(pagerContext: PagerData) {
        report(
            ReportingEvent.PageView(pagerContext, environment.displayTimer.time),
            layoutState.reportingContext(pagerContext = pagerContext)
        )
    }
}

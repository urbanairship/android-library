/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.PagerControllerInfo
import com.urbanairship.android.layout.property.PagerControllerBranching
import com.urbanairship.android.layout.reporting.PagerData
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Controller that manages communication between Pager and PagerIndicator children.
 */
internal class PagerController(
    viewInfo: PagerControllerInfo,
    val view: AnyModel,
    val branching: PagerControllerBranching? = null,
    private val pagerState: SharedState<State.Pager>,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<View, PagerControllerInfo, BaseModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    private val pagerViewCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val completionReported = MutableStateFlow(false)

    init {
        modelScope.launch {
            pagerState.changes.map { it.reportingContext() }.distinctUntilChanged()
                .collect(::reportPageView)
        }

        modelScope.launch {
            environment.eventHandler.layoutEvents
                .filterIsInstance<LayoutEvent.Finish>()
                .collect {
                    stopAndReportPagerSummary()
                }
        }
    }

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = view.createView(context, viewEnvironment, itemProperties)

    private fun reportPageView(pagerContext: PagerData) {
        val event = ReportingEvent.PageView(
            data = ReportingEvent.PageViewData(
                identifier = pagerContext.identifier,
                pageIdentifier = pagerContext.pageId,
                pageIndex = pagerContext.index,
                pageViewCount = incAndGetViewCount(pagerContext.pageId),
                pageCount = pagerContext.count,
                completed = pagerContext.isCompleted
            ),
            context = layoutState.reportingContext(pagerContext = pagerContext)
        )

        report(event)

        environment.pagerTracker.onPageView(
            pageEvent = event.data,
            currentDisplayTime = environment.displayTimer.time.milliseconds
        )

        if (pagerContext.isCompleted) {
            reportCompletion(pagerContext)
        }
    }

    private fun reportCompletion(pagerContext: PagerData) {
        if (completionReported.getAndUpdate { true }) {
            return
        }

        report(
            event = ReportingEvent.PagerComplete(
                data = ReportingEvent.PagerCompleteData(
                    identifier = pagerContext.identifier,
                    pageIndex = pagerContext.index,
                    pageCount = pagerContext.count,
                    pageIdentifier = pagerContext.pageId
                ),
                context = layoutState.reportingContext(pagerContext = pagerContext)
            )
        )
    }

    private fun stopAndReportPagerSummary() {
        val pagerIdentifier = pagerState.changes.value.identifier
        environment.pagerTracker.stop(
            pagerId = pagerIdentifier,
            currentDisplayTime = environment.displayTimer.time.milliseconds
        )

        val summary = environment.pagerTracker
            .generateSummaryEvents()
            .firstOrNull { it.identifier == pagerIdentifier }
            ?: return

        report(
            event = ReportingEvent.PagerSummary(
                data = summary,
                context = layoutState.reportingContext()
            )
        )
    }

    private fun incAndGetViewCount(pageId: String): Int {
        val count = pagerViewCounts.value.getOrElse(pageId) { 0 } + 1
        pagerViewCounts.update { it.toMutableMap().apply { put(pageId, count) } }
        return count
    }
}

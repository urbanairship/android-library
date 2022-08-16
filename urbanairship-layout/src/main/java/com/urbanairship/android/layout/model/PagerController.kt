/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.Logger
import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.event.Event.ViewInit
import com.urbanairship.android.layout.event.EventType
import com.urbanairship.android.layout.event.PagerEvent
import com.urbanairship.android.layout.event.PagerEvent.PageActions
import com.urbanairship.android.layout.event.PagerEvent.Scroll
import com.urbanairship.android.layout.event.ReportingEvent.PageSwipe
import com.urbanairship.android.layout.event.ReportingEvent.PageView
import com.urbanairship.android.layout.info.PagerControllerInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.reporting.PagerData

/**
 * Controller that manages communication between Pager and PagerIndicator children.
 */
internal class PagerController(
    final val view: BaseModel,
    override val identifier: String,
    backgroundColor: Color? = null,
    border: Border? = null,
    environment: ModelEnvironment
) : LayoutModel<PagerControllerInfo>(
    viewType = ViewType.PAGER_CONTROLLER,
    backgroundColor = backgroundColor,
    border = border,
    environment = environment
), Identifiable {
    constructor(info: PagerControllerInfo, env: ModelEnvironment) : this(
        view = env.modelProvider.create(info.view, env),
        identifier = info.identifier,
        backgroundColor = info.backgroundColor,
        border = info.border,
        environment = env
    )

    override val children: List<BaseModel> = listOf(view)

    private var pageIdentifier: String? = null
    private var pageIndex = -1
    private var count = -1
    private var completed = false

    /** Returns `true` if the controller has been initialized with state from a pager view.  */
    private val isInitialized: Boolean
        get() = pageIdentifier != null && pageIndex != -1 && count != -1

    init {
        view.addListener(this)
    }

    override fun onEvent(event: Event, layoutData: LayoutData): Boolean {
        Logger.verbose("onEvent: $event")
        val updatedLayoutData = layoutData.withPagerData(buildPagerData())
        return when (event.type) {
            EventType.PAGER_INIT -> {
                val init = event as PagerEvent.Init
                val wasInitialized = isInitialized
                // Trickle the event to update the pager indicator, if this controller contains one.
                trickleEvent(init, updatedLayoutData)
                // Update our local state.
                reducePagerState(init)
                // If this is the first time we've been initialized, report and handle actions.
                if (!wasInitialized) {
                    reportPageView(init)
                    handlePageActions(init)
                }
                true
            }
            EventType.PAGER_SCROLL -> {
                val scroll = event as Scroll
                // Report the scroll event first, so that the pager context reflects
                // the state of the pager when the swipe was initiated.
                if (!scroll.isInternal) {
                    reportPageSwipe(scroll)
                }
                // Bubble up any actions so that they can be passed along to our actions runner at the top level.
                handlePageActions(scroll)
                // Trickle the event to update the pager indicator, if this controller contains one.
                trickleEvent(scroll, updatedLayoutData)
                // Update our local state.
                reducePagerState(scroll)
                // Report the page view now that we've completed the pager scroll and updated state.
                reportPageView(scroll)
                true
            }
            EventType.BUTTON_BEHAVIOR_PAGER_NEXT, EventType.BUTTON_BEHAVIOR_PAGER_PREVIOUS -> {
                trickleEvent(event, updatedLayoutData)
                false
            }
            EventType.VIEW_INIT -> {
                if ((event as ViewInit).viewType == ViewType.PAGER_INDICATOR) {
                    // Consume indicator init events.
                    true
                } else {
                    super.onEvent(event, updatedLayoutData)
                }
            }
            // Pass along any other events.
            else -> super.onEvent(event, updatedLayoutData)
        }
    }

    private fun reducePagerState(event: PagerEvent) {
        when (event.type) {
            EventType.PAGER_INIT -> {
                val init = event as PagerEvent.Init
                count = init.size
                pageIndex = init.pageIndex
                pageIdentifier = init.pageId
                completed = count == 1
            }
            EventType.PAGER_SCROLL -> {
                val scroll = event as Scroll
                pageIndex = scroll.pageIndex
                pageIdentifier = scroll.pageId
                completed = completed || pageIndex == count - 1
            }
            else -> Unit // Ignore other events.
        }
    }

    private fun reportPageView(event: PagerEvent) {
        val pagerData = buildPagerData()
        bubbleEvent(PageView(pagerData, event.time), LayoutData.pager(pagerData))
    }

    private fun reportPageSwipe(event: Scroll) {
        val data = buildPagerData()
        bubbleEvent(
            PageSwipe(
                data, event.previousPageIndex, event.previousPageId, event.pageIndex, event.pageId
            ), LayoutData.pager(data)
        )
    }

    /**
     * Bubble up any page actions set on the event so that they can be handled by the layout host.
     */
    private fun handlePageActions(event: PagerEvent) {
        if (event.hasPageActions()) {
            bubbleEvent(PageActions(event.pageActions), LayoutData.pager(buildPagerData()))
        }
    }

    private fun buildPagerData(): PagerData =
        PagerData(identifier, pageIndex, pageIdentifier ?: "", count, completed)
}

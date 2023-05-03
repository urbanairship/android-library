/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.PagerInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.AutomatedAction
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.PagerGesture
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.property.earliestNavigationAction
import com.urbanairship.android.layout.util.pagerScrolls
import com.urbanairship.android.layout.view.PagerView
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class PagerModel(
    val items: List<Item>,
    val isSwipeDisabled: Boolean = false,
    val gestures: List<PagerGesture>? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    private val pagerState: SharedState<State.Pager>,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<PagerView, PagerModel.Listener>(
    viewType = ViewType.PAGER,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment,
    properties = properties
) {
    constructor(
        info: PagerInfo,
        items: List<Item>,
        pagerState: SharedState<State.Pager>,
        env: ModelEnvironment,
        props: ModelProperties
    ) : this(
        items = items,
        isSwipeDisabled = info.isSwipeDisabled,
        gestures = info.gestures,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        pagerState = pagerState,
        environment = env,
        properties = props
    )

    class Item(
        val view: AnyModel,
        val identifier: String,
        val displayActions: Map<String, JsonValue>?,
        val automatedActions: List<AutomatedAction>?
    )

    interface Listener : BaseModel.Listener {
        fun scrollTo(position: Int)
    }

    /** Stable viewId for the recycler view.  */
    val recyclerViewId = View.generateViewId()

    val pages = items.map { it.view }

    private val pageViewIds = mutableMapOf<Int, Int>()

    init {
        // Update pager state with our page identifiers
        pagerState.update { state ->
            state.copyWithPageIds(pageIds = items.map { it.identifier })
        }

        // Listen for page changes (or the initial page display)
        // and run any actions for the current page.
        modelScope.launch {

            pagerState.changes
                .map { it.pageIndex to it.lastPageIndex }
                .filter { (pageIndex, lastPageIndex) ->
                    // If current and last are both 0, we're initializing the pager.
                    // Otherwise, we only want to act on changes to the pageIndex.
                    pageIndex == 0 && lastPageIndex == 0 || pageIndex != lastPageIndex
                }
                .collect { (pageIndex, _) ->
                    // Run any actions for the current page.
                    items[pageIndex].displayActions?.let { actions ->
                        runActions(actions)
                    }
                }
        }
    }

    override fun onCreateView(context: Context, viewEnvironment: ViewEnvironment) =
        PagerView(context, this, viewEnvironment).apply {
            id = viewId
        }

    override fun onViewAttached(view: PagerView) {
        // Collect page index changes from state and tell the view to scroll to the current page.
        viewScope.launch {
            pagerState.changes.collect {
                listener?.scrollTo(it.pageIndex)
            }
        }

        // Collect pager scrolls, update pager state, and report
        // the page swipe if it was triggered by the user.
        viewScope.launch {
            view.pagerScrolls().collect { (position, isInternalScroll) ->
                pagerState.update { state ->
                    state.copyWithPageIndex(position)
                }

                if (!isInternalScroll) {
                    reportPageSwipe(pagerState.changes.value)
                }

                // TODO(stories): We could merge these into automatedActions, with 0 delay?
                // Run any actions for the current page.
                items[position].displayActions?.let { actions ->
                    runActions(actions)
                }

                // Run any automated for the current page.
                items[position].automatedActions?.let { actions ->
                    // The delay of the earliest navigation action determines the duration of
                    // the page display, and can be used to determine the progress value for the
                    // currently displayed page.
                    actions.earliestNavigationAction?.let { action ->
                        // TODO(stories): Set timer for action.delay
                    }

                    // TODO(stories): Run any other automated actions.
                    //  If delay is zero, we can run immediately, otherwise schedule the action to
                    //  run after the delay, via the timer.
                }
            }
        }
    }

    /** Returns a stable viewId for the pager item view at the given adapter `position`.  */
    fun getPageViewId(position: Int): Int =
        pageViewIds.getOrPut(position) { View.generateViewId() }

    private fun reportPageSwipe(pagerState: State.Pager) {
        val pagerContext = pagerState.reportingContext()
        report(
            ReportingEvent.PageSwipe(
                pagerContext,
                pagerState.lastPageIndex,
                items[pagerState.lastPageIndex].identifier,
                pagerState.pageIndex,
                items[pagerState.pageIndex].identifier
            ), layoutState.reportingContext(pagerContext = pagerContext)
        )
    }
}

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
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.util.pagerScrolls
import com.urbanairship.android.layout.view.PagerView
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.launch

internal class PagerModel(
    val items: List<Item>,
    val isSwipeDisabled: Boolean = false,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    private val pagerState: SharedState<State.Pager>,
    environment: ModelEnvironment
) : BaseModel<PagerView, PagerModel.Listener>(
    viewType = ViewType.PAGER,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment
) {
    constructor(
        info: PagerInfo,
        items: List<Item>,
        pagerState: SharedState<State.Pager>,
        env: ModelEnvironment
    ) : this(
        items = items,
        isSwipeDisabled = info.isSwipeDisabled,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        pagerState = pagerState,
        environment = env
    )

    class Item(
        val view: AnyModel,
        val identifier: String,
        val actions: Map<String, JsonValue>?
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
            state.copy(pages = items.map { it.identifier })
        }
    }

    override fun onCreateView(context: Context, viewEnvironment: ViewEnvironment) =
        PagerView(context, this, viewEnvironment).apply {
            id = viewId
        }

    override fun onViewAttached(view: PagerView) {
        viewScope.launch {
            pagerState.changes.collect {
                listener?.scrollTo(it.pageIndex)
            }
        }

        viewScope.launch {
            view.pagerScrolls().collect { (position, isInternalScroll) ->
                pagerState.update { state ->
                    state.copyWithPageIndex(position)
                }

                if (!isInternalScroll) {
                    reportPageSwipe(pagerState.changes.value)
                }

                // Run any actions for the current page.
                items[position].actions?.let { actions ->
                    runActions(actions)
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

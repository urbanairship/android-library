/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityManager
import com.urbanairship.UALog
import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.gestures.PagerGestureEvent
import com.urbanairship.android.layout.gestures.PagerGestureEvent.Hold
import com.urbanairship.android.layout.info.AccessibilityAction
import com.urbanairship.android.layout.info.PagerInfo
import com.urbanairship.android.layout.property.AutomatedAction
import com.urbanairship.android.layout.property.ButtonClickBehaviorType
import com.urbanairship.android.layout.property.GestureLocation
import com.urbanairship.android.layout.property.PagerGesture
import com.urbanairship.android.layout.property.earliestNavigationAction
import com.urbanairship.android.layout.property.firstPagerNextOrNull
import com.urbanairship.android.layout.property.hasCancelOrDismiss
import com.urbanairship.android.layout.property.hasPagerNext
import com.urbanairship.android.layout.property.hasPagerPause
import com.urbanairship.android.layout.property.hasPagerPauseOrResumeAction
import com.urbanairship.android.layout.property.hasPagerPrevious
import com.urbanairship.android.layout.property.hasPagerResume
import com.urbanairship.android.layout.util.DelicateLayoutApi
import com.urbanairship.android.layout.util.Timer
import com.urbanairship.android.layout.util.pagerGestures
import com.urbanairship.android.layout.util.pagerScrolls
import com.urbanairship.android.layout.view.PagerView
import com.urbanairship.json.JsonValue
import java.lang.Integer.max
import java.lang.Integer.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class PagerModel(
    viewInfo: PagerInfo,
    val items: List<Item>,
    private val pagerState: SharedState<State.Pager>,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<PagerView, PagerInfo, PagerModel.Listener>(
    viewInfo = viewInfo, environment = environment, properties = properties
) {

    class Item(
        val view: AnyModel,
        val identifier: String,
        val displayActions: Map<String, JsonValue>?,
        val automatedActions: List<AutomatedAction>?,
        val accessibilityActions: List<AccessibilityAction>?
    )

    interface Listener : BaseModel.Listener {

        fun scrollTo(position: Int)
    }

    private var scheduledJob: Job? = null

    /** Stable viewId for the recycler view.  */
    val recyclerViewId = View.generateViewId()

    val pages = items.map { it.view }

    private val pageViewIds = mutableMapOf<Int, Int>()

    private var navigationActionTimer: Timer? = null
    private val automatedActionsTimers: MutableList<Timer> = ArrayList()

    private var accessibilityListener: AccessibilityManager.TouchExplorationStateChangeListener? =
        null

    init {
        // Update pager state with our page identifiers
        pagerState.update { state ->
            state.copyWithPageIds(pageIds = items.map { it.identifier })
                .copyWithDurations(durations = items.map { it.automatedActions?.earliestNavigationAction?.delay })
        }

        // Listen for page changes (or the initial page display)
        // and run any actions for the current page.
        modelScope.launch {

            pagerState.changes.filter {
                    // If current and last are both 0, we're initializing the pager.
                    // Otherwise, we only want to act on changes to the pageIndex.

                    (it.pageIndex == 0 && it.lastPageIndex == 0 || it.pageIndex != it.lastPageIndex) && it.progress == 0
                }.collect {
                    // Clear any automated actions scheduled for the previous page.
                    clearAutomatedActions(it.lastPageIndex)

                    // Handle any actions defined for the current page.
                    items[it.pageIndex].run {
                        handlePageActions(displayActions, automatedActions)

                        // Check if the current page has any automated pause/resume actions
                        val hasPauseOrResumeAction = automatedActions?.hasPagerPauseOrResumeAction == true

                        if (it.isTouchExplorationEnabled) {
                            // Always pause for accessibility
                            pauseStory()
                        } else if (it.isMediaPaused) {
                            // Media not ready, pause until ready
                            pauseStory()
                        } else {
                            // Resume if either:
                            // - Media just became ready (wasMediaPaused)
                            // - OR no automated pause/resume actions exist
                            if (it.wasMediaPaused || !hasPauseOrResumeAction) {
                                resumeStory()
                            }
                        }
                    }
                }
        }
    }

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = PagerView(context, this, viewEnvironment).apply {
        id = viewId
    }

    override fun onViewAttached(view: PagerView) {
        // Collect page index changes from state and tell the view to scroll to the current page.
        viewScope.launch {
            pagerState.changes
                .map { it.pageIndex to it.lastPageIndex }
                .filter { (pageIndex, lastPageIndex) -> pageIndex != lastPageIndex }
                .distinctUntilChanged()
                .collect { (pageIndex, _) ->
                    listener?.scrollTo(pageIndex)
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
            }
        }

        // If we have gestures defined, collect events from the view and handle them.
        if (viewInfo.gestures != null) {
            viewScope.launch {
                view.pagerGestures().collect {
                    handleGesture(it)
                }
            }
        } else {
            UALog.v { "No gestures defined." }
        }

        // Set up accessibility actions and manage touch exploration state
        val accessibilityManager =
            view.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        accessibilityManager?.let { am ->
            accessibilityListener =
                AccessibilityManager.TouchExplorationStateChangeListener { enabled ->
                    updateTouchExplorationState(enabled)
                }.also {
                    am.addTouchExplorationStateChangeListener(it)
                }
            updateTouchExplorationState(am.isTouchExplorationEnabled)
        }

        viewScope.launch {
            pagerState.changes
                .map { it to items[it.pageIndex] }
                .distinctUntilChanged()
                .collect { (state, currentItem) ->
                    view.setAccessibilityActions(currentItem.accessibilityActions) { action ->
                        handleAccessibilityAction(action, state)
                    }
                }
        }
    }

    override fun onViewDetached(view: PagerView) {
        clearAutomatedActions()

        val accessibilityManager =
            view.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        accessibilityManager?.let { am ->
            accessibilityListener?.let {
                am.removeTouchExplorationStateChangeListener(it)
            }
        }
    }

    /** Returns a stable viewId for the pager item view at the given adapter `position`.  */
    fun getPageViewId(position: Int): Int = pageViewIds.getOrPut(position) { View.generateViewId() }

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

    private fun reportGesture(gesture: PagerGesture, pagerState: State.Pager) {
        val pagerContext = pagerState.reportingContext()
        report(
            ReportingEvent.PageGesture(
                gesture.identifier,
                gesture.reportingMetadata,
                pagerContext
            ), layoutState.reportingContext(pagerContext = pagerContext)
        )
    }

    private fun reportAutomatedAction(action: AutomatedAction, pagerState: State.Pager) {
        val pagerContext = pagerState.reportingContext()
        report(
            ReportingEvent.PageAction(
                action.identifier,
                action.reportingMetadata,
                pagerContext
            ),
            layoutState.reportingContext(pagerContext = pagerContext)
        )
    }

    private fun handleAccessibilityAction(action: AccessibilityAction, pagerState: State.Pager) {
        action.behaviors?.let { evaluateClickBehaviors(it) }

        runActions(action.actions)

        // TODO: Report the accessibility action
    }

    private suspend fun handlePageActions(
        displayActions: Map<String, JsonValue>?,
        automatedActions: List<AutomatedAction>?
    ) {
        // Run any display actions for the current page.
        runActions(displayActions)

        // Run any automated for the current page.
        automatedActions?.let { actions ->
            // The delay of the earliest navigation action determines the duration of
            // the page display, and can be used to determine the progress value for the
            // currently displayed page.
            actions.earliestNavigationAction?.let { action ->
                navigationActionTimer = object : Timer(action.delay.toLong() * 1000L) {
                    override fun onFinish() {
                        // Clean up the progress timer and this navigation action timer.
                        scheduledJob?.cancel()
                        automatedActionsTimers.remove(this)

                        action.behaviors?.let { evaluateClickBehaviors(it) }
                        action.actions?.let { runActions(it) }

                        reportAutomatedAction(action, pagerState.changes.value)
                    }
                }.apply {
                    start()
                    scheduledJob = modelScope.launch {
                        while (isActive) {
                            pagerState.update { state ->
                                state.copy(progress = progress)
                            }
                            delay(100)
                        }
                    }
                }
            }

            // Run the other automated actions
            actions.filter { it != actions.earliestNavigationAction }.forEach { action ->
                if (action.delay == 0) {
                    //  If delay is zero run immediately
                    action.behaviors?.let { evaluateClickBehaviors(it) }
                    action.actions?.let { runActions(it) }
                    reportAutomatedAction(action, pagerState.changes.value)
                } else {
                    // otherwise schedule the action
                    scheduleAutomatedAction(action)
                }
            }
        }
    }

    private fun scheduleAutomatedAction(action: AutomatedAction) {
        val timer = object : Timer(action.delay.toLong() * 1000L) {
            override fun onFinish() {
                automatedActionsTimers.remove(this)

                action.behaviors?.let { evaluateClickBehaviors(it) }
                action.actions?.let { runActions(it) }

                reportAutomatedAction(action, pagerState.changes.value)
            }
        }
        automatedActionsTimers.add(timer)
        timer.start()
    }

    private fun handleGesture(event: PagerGestureEvent) {
        UALog.v { "handleGesture: $event" }

        val triggeredGestures = when (event) {
            is PagerGestureEvent.Tap -> viewInfo.gestures.orEmpty()
                .filterIsInstance<PagerGesture.Tap>()
                .filter { it.location == event.location || it.location == GestureLocation.ANY }
                .map { it to it.behavior }

            is PagerGestureEvent.Swipe -> viewInfo.gestures.orEmpty()
                .filterIsInstance<PagerGesture.Swipe>().filter { it.direction == event.direction }
                .map { it to it.behavior }

            is Hold -> viewInfo.gestures.orEmpty().filterIsInstance<PagerGesture.Hold>().map {
                    it to when (event.action) {
                        Hold.Action.PRESS -> it.pressBehavior
                        Hold.Action.RELEASE -> it.releaseBehavior
                    }
                }
        }

        triggeredGestures.forEach { (gesture, gestureBehaviors) ->
            gestureBehaviors.actions?.let { runActions(it) }
            gestureBehaviors.behaviors?.let { evaluateClickBehaviors(it) }

            reportGesture(gesture, pagerState.changes.value)
        }
    }

    private fun evaluateClickBehaviors(behaviors: List<ButtonClickBehaviorType>) {
        if (behaviors.hasCancelOrDismiss) {
            // If there's only a CANCEL or DISMISS, and no FORM_SUBMIT, handle
            // immediately. We don't need to handle pager behaviors, as the layout
            // will be dismissed.
            handleDismiss()
        } else {
            // No FORM_SUBMIT, CANCEL, or DISMISS, so we only need to
            // handle pager behaviors.
            if (behaviors.hasPagerNext) {
                handlePagerNext(fallback = behaviors.pagerNextFallback)
            }
            if (behaviors.hasPagerPrevious) {
                handlePagerPrevious()
            }
            if (behaviors.hasPagerPause) {
                pauseStory()
            }
            if (behaviors.hasPagerResume) {
                resumeStory()
            }
        }
    }

    private fun handlePagerNext(fallback: PagerNextFallback) {
        @OptIn(DelicateLayoutApi::class)
        val hasNext = pagerState.value.hasNext

        when {
            !hasNext && fallback == PagerNextFallback.FIRST -> pagerState.update { state ->
                state.copyWithPageIndexAndResetProgress(0)
            }

            !hasNext && fallback == PagerNextFallback.DISMISS -> handleDismiss()
            else -> pagerState.update { state ->
                state.copyWithPageIndex(min(state.pageIndex + 1, state.pageIds.size - 1))
            }
        }
    }

    private fun handlePagerPrevious() {
        pagerState.update { state ->
            state.copyWithPageIndex(max(state.pageIndex - 1, 0))
        }
    }

    private fun handleDismiss() {
        clearAutomatedActions()

        report(
            ReportingEvent.DismissFromOutside(environment.displayTimer.time),
            layoutState.reportingContext()
        )
        broadcast(LayoutEvent.Finish)
    }

    private fun pauseStory() {
        UALog.v { "pause story" }
        navigationActionTimer?.stop()
        for (timer in automatedActionsTimers) {
            timer.stop()
        }
        pagerState.update {
            it.copyWithStoryPaused(true)
        }
    }

    private fun resumeStory() {
        if (navigationActionTimer?.isStarted != true || automatedActionsTimers.isNotEmpty()) {
            UALog.v { "resume story" }
        }

        navigationActionTimer?.start()
        for (timer in automatedActionsTimers) {
            timer.start()
        }
        pagerState.update {
            it.copyWithStoryPaused(false)
        }
    }

    private fun clearAutomatedActions(pageIndex: Int? = null) {
        navigationActionTimer?.stop()
        scheduledJob?.cancel()

        for (timer in automatedActionsTimers) {
            timer.stop()
        }

        if (automatedActionsTimers.isNotEmpty()) {
            UALog.v {
                if (pageIndex != null) {
                    "Cleared all automated actions! For page: '$pageIndex'"
                } else {
                    @OptIn(DelicateLayoutApi::class)
                    "Cleared all automated actions! For pager: '${pagerState.value.identifier}'"
                }
            }
        }

        automatedActionsTimers.clear()
    }

    private fun updateTouchExplorationState(enabled: Boolean) {
        pagerState.update {
            it.copyWithTouchExplorationState(enabled)
        }
        if (enabled) {
            pauseStory()
        } else {
            resumeStory()
        }
    }
}

internal enum class PagerNextFallback {
    NONE,
    DISMISS,
    FIRST
}

internal val List<ButtonClickBehaviorType>.pagerNextFallback: PagerNextFallback
    get() = firstPagerNextOrNull()?.let {
        when (it) {
            ButtonClickBehaviorType.PAGER_NEXT_OR_DISMISS -> PagerNextFallback.DISMISS
            ButtonClickBehaviorType.PAGER_NEXT_OR_FIRST -> PagerNextFallback.FIRST
            else -> PagerNextFallback.NONE
        }
    } ?: PagerNextFallback.NONE

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
import com.urbanairship.android.layout.property.DisableSwipeSelector
import com.urbanairship.android.layout.property.GestureLocation
import com.urbanairship.android.layout.property.Outcome
import com.urbanairship.android.layout.property.Outcome.PagerJumpNavigation.Page
import com.urbanairship.android.layout.property.Outcome.PagerPlayback.Command
import com.urbanairship.android.layout.property.Outcome.PagerStepNavigation.BoundaryBehavior
import com.urbanairship.android.layout.property.Outcome.PagerStepNavigation.Direction
import com.urbanairship.android.layout.property.PageBranching
import com.urbanairship.android.layout.property.PagerGesture
import com.urbanairship.android.layout.property.earliestNavigationAction
import com.urbanairship.android.layout.property.firstPagerNextOrNull
import com.urbanairship.android.layout.property.hasPagerPauseOrResumeAction
import com.urbanairship.android.layout.util.DelicateLayoutApi
import com.urbanairship.android.layout.util.Timer
import com.urbanairship.android.layout.util.pagerGestures
import com.urbanairship.android.layout.util.pagerScrolls
import com.urbanairship.android.layout.view.PagerView
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PagerModel(
    viewInfo: PagerInfo,
    availablePages: List<Item>,
    private val pagerState: SharedState<State.Pager>,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<PagerView, PagerInfo, PagerModel.Listener>(
    viewInfo = viewInfo, environment = environment, properties = properties
) {

    class Item(
        val view: AnyModel,
        val identifier: String,
        val automatedActions: List<AutomatedAction>?,
        val accessibilityActions: List<AccessibilityAction>?,
        val displayOutcomes: List<Outcome>,
        val branching: PageBranching?
    )

    interface Listener : BaseModel.Listener {
        fun onDataUpdated()
        fun scrollTo(position: Int, animate: Boolean)
    }

    private var scheduledJob: Job? = null
    private var isManuallyPaused = false

    /** Stable viewId for the recycler view.  */
    val recyclerViewId = View.generateViewId()

    private var _allPages: List<Item> = emptyList()
    var pages: List<AnyModel> = emptyList()
        private set

    private val pageViewIds = mutableMapOf<Int, Int>()

    private var navigationActionTimer: Timer? = null
    private val automatedActionsTimers: MutableList<Timer> = ArrayList()

    private var accessibilityListener: AccessibilityManager.TouchExplorationStateChangeListener? =
        null

    private val branchControl: PagerBranchControl?
    private val lastDisplayedPageId = MutableStateFlow<String?>(null)

    val isSinglePage: Boolean
        get() = branchControl == null && pages.size < 2

    /**
     * Pager-local outcome processor that directly manipulates pager state
     * instead of broadcasting layout events (which would go through the event
     * bus and back). This keeps pager operations synchronous with the caller.
     */
    private val pagerOutcomeProcessor = object : ThomasOutcomeProcessor(environment, layoutState) {
        override suspend fun handlePagerStep(outcome: Outcome.PagerStepNavigation) {
            branchControl?.requestPathRebuild()
            when (outcome.direction) {
                Direction.NEXT -> when (outcome.boundaryBehavior) {
                    BoundaryBehavior.DISMISS ->
                        handlePagerNext(PagerNextFallback.DISMISS)
                    BoundaryBehavior.WRAP ->
                        handlePagerNext(PagerNextFallback.FIRST)
                    BoundaryBehavior.IGNORE ->
                        handlePagerNext(PagerNextFallback.NONE)
                }
                Direction.PREVIOUS -> handlePagerPrevious()
            }
        }

        override suspend fun handlePagerJump(outcome: Outcome.PagerJumpNavigation) {
            branchControl?.requestPathRebuild()
            when (outcome.page) {
                Page.START -> resolve(PageRequest.FIRST)
                Page.END -> resolve(PageRequest.LAST)
            }
        }

        override fun handlePagerPlayback(outcome: Outcome.PagerPlayback) {
            when (outcome.command) {
                Command.PAUSE -> {
                    isManuallyPaused = true
                    pauseStory()
                }
                Command.RESUME -> {
                    isManuallyPaused = false
                    resumeStory()
                }
                Command.TOGGLE -> handlePagerPauseToggle()
            }
        }
    }

    /** Pager-specific outcome handler that handles dismiss with reporting. */
    private val pagerOutcomeHandler: suspend (HandlerOutcome) -> Unit = { outcome ->
        when (outcome) {
            is HandlerOutcome.Dismiss -> handleDismiss()
            else -> defaultHandler(outcome)
        }
    }

    init {
        @OptIn(DelicateLayoutApi::class)
        val state = pagerState.value
        val branching = state.branching

        if (branching != null) {
            branchControl = PagerBranchControl(
                availablePages = availablePages,
                controllerBranching = branching,
                thomasState = environment.layoutState.thomasState,
                onBranchUpdated = ::onPagesDataUpdated,
                outcomeRunner = { outcomes -> pagerOutcomeProcessor.process(outcomes, handlerOutcome = pagerOutcomeHandler) },
            )
            wireBranchControlFlows(branchControl)
        } else {
            branchControl = null
            onPagesDataUpdated(availablePages, state.completed)
        }

        modelScope.launch {
            pagerState.changes
                .filter {
                    (it.pageIndex == 0 && it.lastPageIndex == 0 || it.pageIndex != it.lastPageIndex)
                            && it.progress == 0
                            && it.isScrolling.not()
                }
                .collect {
                    val currentPage = it.currentPageId?.let { id ->
                        _allPages.firstOrNull { page -> page.identifier == id }
                    } ?: return@collect

                    UALog.v {
                        "ThomasPager pageChangeListener: pageIndex=${it.pageIndex} lastPageIndex=${it.lastPageIndex} " +
                        "currentPageId=${it.currentPageId} previousPageId=${it.previousPageId} " +
                        "lastDisplayedPageId=${lastDisplayedPageId.value} pageIds=${it.pageIds}"
                    }

                    if (lastDisplayedPageId.value != currentPage.identifier) {
                        UALog.v { "ThomasPager pageChangeListener: new page ${currentPage.identifier}, running side effects" }
                        clearAutomatedActions(it.lastPageIndex)
                        if (it.lastPageIndex > it.pageIndex) {
                            it.previousPageId?.let { pageId -> branchControl?.removeFromHistory(pageId) }
                        }

                        lastDisplayedPageId.update { currentPage.identifier }

                        pagerOutcomeProcessor.process(currentPage.displayOutcomes, handlerOutcome = pagerOutcomeHandler)

                        handleAutomatedActions(currentPage.automatedActions)
                        it.currentPageId?.let { pageId -> branchControl?.addToHistory(pageId) }
                    }

                    val hasPauseOrResumeAction = currentPage.automatedActions?.hasPagerPauseOrResumeAction == true

                    if (it.isTouchExplorationEnabled) {
                        UALog.v { "Page change: pausing story (touch exploration)" }
                        pauseStory()
                    } else if (isManuallyPaused) {
                        UALog.v { "Page change: staying paused (manually paused)" }
                        pauseStory()
                    } else if (it.isMediaPaused) {
                        UALog.v { "Page change: pausing timers (media loading)" }
                        navigationActionTimer?.stop()
                        for (timer in automatedActionsTimers) {
                            timer.stop()
                        }
                    } else if (!hasPauseOrResumeAction) {
                        UALog.v { "Page change: resuming story" }
                        resumeStory()
                    } else {
                        UALog.v { "Page change: deferring to automated pause/resume actions" }
                    }
                }
        }

        modelScope.launch {
            pagerState.changes
                .map { it.isMediaPaused }
                .distinctUntilChanged()
                .filter { !it }
                .collect {
                    if (!isManuallyPaused) {
                        UALog.v { "Media ready: restarting timers" }
                        navigationActionTimer?.start()
                        for (timer in automatedActionsTimers) {
                            timer.start()
                        }
                    }
                }
        }

        modelScope.launch { wireSwipeSelector() }

        // Handle pager events from ButtonModel and other sources.
        environment.layoutEvents
            .filterIsInstance<LayoutEvent.Pager>()
            .onEach { event ->
                when (event) {
                    is LayoutEvent.Pager.Next -> handlePagerNext(event.fallback)
                    is LayoutEvent.Pager.Previous -> handlePagerPrevious()
                    is LayoutEvent.Pager.Start -> resolve(PageRequest.FIRST)
                    is LayoutEvent.Pager.End -> resolve(PageRequest.LAST)
                    is LayoutEvent.Pager.Pause -> {
                        isManuallyPaused = true
                        pauseStory()
                    }
                    is LayoutEvent.Pager.Resume -> {
                        isManuallyPaused = false
                        resumeStory()
                    }
                    is LayoutEvent.Pager.PauseToggle -> handlePagerPauseToggle()
                    else -> {}
                }
            }
            .launchIn(modelScope)
    }

    private fun onPagesDataUpdated(updated: List<Item>, completed: Boolean) {
        _allPages = updated
        pages = updated.map { it.view }

        // Update pager state with our page identifiers
        pagerState.update { state ->
            val newPageIds = updated.map { it.identifier }
            val newDurations = updated.map { it.automatedActions?.earliestNavigationAction?.delay }
            val newCompleted = if (state.branching == null) updated.size == 1 else completed

            // When the page list changes due to branching re-evaluation, the current
            // pageIndex may point to a different page. Find the current page's identifier
            // in the new list and adjust the index to stay on the same page.
            val currentId = state.currentPageId
            val adjustedIndex = if (currentId != null) {
                newPageIds.indexOf(currentId).takeIf { it >= 0 }
            } else {
                null
            } ?: state.pageIndex.coerceIn(0, newPageIds.lastIndex.coerceAtLeast(0))

            // Keep lastPageIndex in sync so a pure re-index doesn't look like
            // a page transition to downstream listeners.
            val adjustedLastIndex = if (adjustedIndex != state.pageIndex) {
                adjustedIndex
            } else {
                state.lastPageIndex
            }

            UALog.v {
                "ThomasPager onPagesDataUpdated: oldPageIds=${state.pageIds} newPageIds=$newPageIds " +
                "pageIndex ${state.pageIndex} → $adjustedIndex lastPageIndex ${state.lastPageIndex} → $adjustedLastIndex " +
                "currentId=$currentId"
            }

            state.copy(
                pageIndex = adjustedIndex,
                lastPageIndex = adjustedLastIndex,
                pageIds = newPageIds,
                durations = newDurations,
                completed = newCompleted,
            )
        }

        viewScope.launch {
            listener?.onDataUpdated()
        }
    }

    private fun wireBranchControlFlows(control: PagerBranchControl) {
        modelScope.launch {
            control.isComplete.collect { complete ->
                pagerState.update { it.copy(completed = complete) }
            }
        }
    }

    private suspend fun wireSwipeSelector() {
        val selectors = viewInfo.disableSwipeWhen ?: return

        environment.layoutState.thomasState.collect { state ->
            val matched = selectors.firstOrNull { it.predicate?.apply(state) ?: true }
            when(matched?.direction) {
                DisableSwipeSelector.Direction.HORIZONTAL -> pagerState.update { it.copy(isScrollDisabled = true) }
                null -> {
                    if (pagerState.changes.value.isScrollDisabled) {
                        pagerState.update { it.copy(isScrollDisabled = false) }
                    }
                }
            }
        }
    }

    private fun resolve(request: PageRequest): Boolean {
        scheduledJob?.cancel()

        var ensurePageId: String? = null

        pagerState.update {
            val copy = it.copyWithPageRequest(request)
            UALog.v { "ThomasPager resolve($request): pageIndex ${it.pageIndex} → ${copy.pageIndex} (pageIds=${it.pageIds})" }

            if (copy.pageIndex != it.pageIndex) {
                branchControl?.onPageRequest(request)

                if (request == PageRequest.NEXT) {
                    ensurePageId = copy.currentPageId
                }
            }

            copy
        }

        ensurePageId?.let { branchControl?.ensureInHistory(it) }

        return true
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
        // The first emission after each attach skips the scroll animation, so a restored
        // pageIndex (or one re-applied after the view is detached and reattached, e.g. on a
        // tab switch) snaps directly to its saved page instead of animating page-by-page.
        viewScope.launch {
            var isFirstScroll = true
            pagerState.changes
                .map { it.pageIndex to it.lastPageIndex }
                .filter { (pageIndex, lastPageIndex) -> pageIndex != lastPageIndex }
                .distinctUntilChanged()
                .collect { (pageIndex, _) ->
                    val animate = !isFirstScroll
                    isFirstScroll = false
                    withContext(Dispatchers.Main) {
                        listener?.scrollTo(pageIndex, animate)
                    }
                }
        }

        viewScope.launch {
            view.pagerScrolls().collect { (position, isInternalScroll) ->
                val current = pagerState.changes.value.pageIndex
                UALog.v { "ThomasPager pagerScroll: position=$position current=$current isInternalScroll=$isInternalScroll" }
                val request = makePageRequest(position)
                if (request != null) {
                    if (!resolve(request)) {
                        return@collect
                    }

                    if (!isInternalScroll) {
                        reportPageSwipe(pagerState.changes.value)
                    }
                }
            }
        }

        viewScope.launch {
            view.isScrolling.collect { isScrolling ->
                pagerState.update { it.copyWithScrolling(isScrolling) }
            }
        }

        if (viewInfo.gestures != null) {
            viewScope.launch {
                view.pagerGestures().collect {
                    handleGesture(it)
                }
            }
        } else {
            UALog.v { "No gestures defined." }
        }

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
                .map { state ->  state to _allPages.firstOrNull { it.identifier == state.currentPageId } }
                .distinctUntilChanged()
                .collect { (state, currentItem) ->
                    view.setAccessibilityActions(currentItem?.accessibilityActions) { action ->
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

    fun getPageViewId(position: Int): Int = pageViewIds.getOrPut(position) { View.generateViewId() }

    private fun makePageRequest(toPosition: Int): PageRequest? {
        val current = pagerState.changes.value.pageIndex

        return if (toPosition > current) {
            PageRequest.NEXT
        } else if (toPosition < current) {
            PageRequest.BACK
        } else {
            null
        }
    }

    private fun reportPageSwipe(pagerState: State.Pager) {
        val currentPage = pagerState.currentPageId ?: return
        val previousPage = pagerState.previousPageId ?: return

        val pagerContext = pagerState.reportingContext(emptyList())
        val event = ReportingEvent.PageSwipe(
            data = ReportingEvent.PageSwipeData(
                identifier = pagerContext.identifier,
                fromPageIndex = pagerState.lastPageIndex,
                fromPageIdentifier = previousPage,
                toPageIndex = pagerState.pageIndex,
                toPageIdentifier = currentPage
            ),
            context = layoutState.reportingContext(pagerContext = pagerContext)
        )

        report(event)
    }

    private fun reportGesture(gesture: PagerGesture, pagerState: State.Pager) {
        val pagerContext = pagerState.reportingContext(emptyList())

        val event = ReportingEvent.Gesture(
            data = ReportingEvent.GestureData(
                identifier = gesture.identifier,
                reportingMetadata = gesture.reportingMetadata
            ),
            context = layoutState.reportingContext(pagerContext = pagerContext)
        )

        report(event)
    }

    private fun reportAutomatedAction(action: AutomatedAction, pagerState: State.Pager) {
        val pagerContext = pagerState.reportingContext(emptyList())
        val event = ReportingEvent.PageAction(
            data = ReportingEvent.PageActionData(
                identifier = action.identifier,
                metadata = action.reportingMetadata
            ),
            context = layoutState.reportingContext(pagerContext = pagerContext)
        )

        report(event)
    }

    private fun handleAccessibilityAction(action: AccessibilityAction, pagerState: State.Pager) {
        modelScope.launch {
            pagerOutcomeProcessor.process(action.outcomes, handlerOutcome = pagerOutcomeHandler)
        }
    }

    private fun handleAutomatedActions(automatedActions: List<AutomatedAction>?) {
        automatedActions?.let { actions ->
            actions.earliestNavigationAction?.let { action ->
                navigationActionTimer = object : Timer(action.delay.toLong() * 1000L) {
                    override fun onFinish() {
                        scheduledJob?.cancel()
                        automatedActionsTimers.remove(this)

                        modelScope.launch {
                            pagerOutcomeProcessor.process(action.outcomes, handlerOutcome = pagerOutcomeHandler)
                            reportAutomatedAction(action, pagerState.changes.value)
                        }
                    }
                }.apply {
                    start()
                    scheduledJob = modelScope.launch {
                        while (isActive) {
                            pagerState.update { state ->
                                state.copy(progress = getProgress())
                            }
                            delay(100)
                        }
                    }
                }
            }

            actions.filter { it != actions.earliestNavigationAction }.forEach { action ->
                if (action.delay == 0) {
                    modelScope.launch {
                        pagerOutcomeProcessor.process(action.outcomes, handlerOutcome = pagerOutcomeHandler)
                        reportAutomatedAction(action, pagerState.changes.value)
                    }
                } else {
                    scheduleAutomatedAction(action)
                }
            }
        }
    }

    private fun scheduleAutomatedAction(action: AutomatedAction) {
        val timer = object : Timer(action.delay.toLong() * 1000L) {
            override fun onFinish() {
                automatedActionsTimers.remove(this)

                modelScope.launch {
                    pagerOutcomeProcessor.process(action.outcomes, handlerOutcome = pagerOutcomeHandler)
                    reportAutomatedAction(action, pagerState.changes.value)
                }
            }
        }
        automatedActionsTimers.add(timer)
        timer.start()
    }

    private suspend fun handleGesture(event: PagerGestureEvent) {
        UALog.v { "handleGesture: $event" }

        val triggeredGestures: List<Pair<PagerGesture, List<Outcome>?>> = when (event) {
            is PagerGestureEvent.Tap -> viewInfo.gestures.orEmpty()
                .filterIsInstance<PagerGesture.Tap>()
                .filter { it.location == event.location || it.location == GestureLocation.ANY }
                .map { it to it.outcomes }

            is PagerGestureEvent.Swipe -> viewInfo.gestures.orEmpty()
                .filterIsInstance<PagerGesture.Swipe>().filter { it.direction == event.direction }
                .map { it to it.outcomes }

            is Hold -> viewInfo.gestures.orEmpty().filterIsInstance<PagerGesture.Hold>().map {
                it to when (event.action) {
                    Hold.Action.PRESS -> it.pressOutcomes
                    Hold.Action.RELEASE -> it.releaseOutcomes
                }
            }
        }

        triggeredGestures.forEach { (gesture, outcomes) ->
            pagerOutcomeProcessor.process(outcomes, handlerOutcome = pagerOutcomeHandler)
            reportGesture(gesture, pagerState.changes.value)
        }
    }

    private fun handlePagerNext(fallback: PagerNextFallback) {
        branchControl?.requestPathRebuild()
        @OptIn(DelicateLayoutApi::class)
        if (pagerState.value.hasNext) {
            resolve(PageRequest.NEXT)
        } else {
            when (fallback) {
                PagerNextFallback.NONE -> {}
                PagerNextFallback.DISMISS -> handleDismiss()
                PagerNextFallback.FIRST -> resolve(PageRequest.FIRST)
            }
        }
    }

    private fun handlePagerPrevious() {
        resolve(PageRequest.BACK)
    }

    @OptIn(DelicateLayoutApi::class)
    private fun handlePagerPauseToggle() {
        if (pagerState.value.isManuallyPaused) {
            isManuallyPaused = false
            resumeStory()
        } else {
            isManuallyPaused = true
            pauseStory()
        }
    }

    private fun handleDismiss() {
        clearAutomatedActions()

        report(
            event = ReportingEvent.Dismiss(
                data = ReportingEvent.DismissData.UserDismissed,
                displayTime = environment.displayTimer.time.milliseconds,
                context = layoutState.reportingContext()
            )
        )

        broadcast(LayoutEvent.Finish())
    }

    private fun pauseStory() {
        UALog.v { "Pausing story" }

        navigationActionTimer?.stop()
        for (timer in automatedActionsTimers) {
            timer.stop()
        }
        pagerState.update {
            it.copyWithStoryManuallyPaused(true)
        }
    }

    private fun resumeStory() {
        UALog.v { "Resuming story" }

        navigationActionTimer?.start()
        for (timer in automatedActionsTimers) {
            timer.start()
        }
        pagerState.update {
            it.copyWithStoryManuallyPaused(false)
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
        } else if (!isManuallyPaused) {
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

internal enum class PageRequest {
    NEXT,
    BACK,
    FIRST,
    LAST
}

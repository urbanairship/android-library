/* Copyright Airship and Contributors */

package com.urbanairship.embedded

import androidx.annotation.RestrictTo
import com.urbanairship.Airship
import com.urbanairship.UALog
import com.urbanairship.android.layout.AirshipEmbeddedViewManager
import com.urbanairship.android.layout.EmbeddedDisplayRequest
import com.urbanairship.android.layout.EmbeddedDisplayRequestResult
import com.urbanairship.android.layout.display.DisplayArgs
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.embedded.ai.DefaultEmbeddedAiSelector
import com.urbanairship.embedded.ai.EmbeddedAiSelector
import com.urbanairship.embedded.ai.EmbeddedSelectionRequest
import com.urbanairship.embedded.ai.EmbeddedSelectionStrategy
import com.urbanairship.json.JsonMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object EmbeddedViewManager : AirshipEmbeddedViewManager {

    private val pending: MutableMap<String, List<EmbeddedDisplayRequest>> = mutableMapOf()
    private val viewsFlow = MutableStateFlow<Map<String, List<EmbeddedDisplayRequest>>>(emptyMap())

    private val lastViewed: MutableMap<String, String> = mutableMapOf()
    private val lastViewedLock =  ReentrantLock()

    /**
     * Resolved lazily rather than at class-init: this is an object, so it is created on first
     * touch, which can be before `takeOff`.
     */
    @Volatile
    internal var aiSelector: EmbeddedAiSelector? = null
        // `takeIf` would not do: it runs after its receiver is built, and building one reads
        // `Airship.internalAi`, which throws before takeOff.
        get() = field
            ?: if (Airship.isFlyingOrTakingOff) DefaultEmbeddedAiSelector(Airship.internalAi) else null

    public override fun addPending(
        embeddedViewId: String,
        viewInstanceId: String,
        priority: Int,
        extras: JsonMap,
        layoutInfoProvider: () -> LayoutInfo?,
        displayArgsProvider: () -> DisplayArgs
    ) {
        val pendingForView = pending[embeddedViewId]

        val request = EmbeddedDisplayRequest(
            embeddedViewId = embeddedViewId,
            viewInstanceId = viewInstanceId,
            priority = priority,
            extras = extras,
            layoutInfoProvider = layoutInfoProvider,
            displayArgsProvider = displayArgsProvider
        )

        if (pendingForView.isNullOrEmpty()) {
            pending[embeddedViewId] = listOf(request)
        } else {
            pending[embeddedViewId] = pendingForView + request
        }

        UALog.v { "Embedded view '$embeddedViewId' has ${pending[embeddedViewId]?.size} pending" }

        viewsFlow.value = pending.toMap()
    }

    override fun dismissAll(embeddedViewId: String) {
        pending[embeddedViewId] = emptyList()
        UALog.v { "Embedded view '$embeddedViewId' has ${pending[embeddedViewId]?.size} pending" }

        viewsFlow.value = pending.toMap()
    }

    override fun dismiss(embeddedViewId: String, viewInstanceId: String) {
        val pendingForView = pending[embeddedViewId] ?: return

        // Remove the request for the given view instance ID from the list of pending requests
        pending[embeddedViewId] = pendingForView.filterNot { it.viewInstanceId == viewInstanceId }
        UALog.v { "Embedded view '$embeddedViewId' has ${pending[embeddedViewId]?.size} pending" }

        viewsFlow.value = pending.toMap()
    }

    override fun recordDisplayed(embeddedViewId: String, viewInstanceId: String) {
        lastViewedLock.withLock { lastViewed[embeddedViewId] = viewInstanceId }
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun allPending(): Flow<List<EmbeddedDisplayRequest>> {
        @OptIn(ExperimentalCoroutinesApi::class)
        return viewsFlow.flatMapConcat {
            flowOf(it.values.flatten())
        }
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun displayRequests(
        embeddedViewId: String,
        selection: AirshipEmbeddedSelection,
        filter: AirshipEmbeddedFilter?,
        session: EmbeddedSelectionSession?,
        scope: CoroutineScope,
    ): Flow<EmbeddedDisplayRequestResult> {

        // This assumes displayRequests will be subscribed only when its actually
        // visible/attached to window. The first thing that subscribes will cause any
        // subsequent calls to this method to get the same EmbeddedDisplayRequest until
        // it is no longer in the listing.

        // Narrowed to this view before anything else runs. `viewsFlow` carries every embedded
        // view's pending list, so an unrelated view's add or dismiss publishes a new map and
        // wakes this collector too — which for the AI branch would blank the view and re-rank
        // content already on screen.
        //
        // Filtered here too, ahead of every selection: an ineligible instance can't be
        // displayed, so ordering it, holding it on screen, or spending a model candidate slot
        // on it would all be wasted — and scoring one would let it skew the candidates that
        // can actually display.
        val pendingForView = viewsFlow
            .map { it[embeddedViewId].orEmpty() }
            .distinctUntilChanged()
            .map { forView ->
                filter?.let { eligible -> forView.filter { eligible(it.embeddedInfo) } } ?: forView
            }

        val results = if (selection is AirshipEmbeddedSelection.ByAi) {
            aiDisplayRequests(embeddedViewId, selection, pendingForView, session)
        } else {
            pendingForView.map { select(embeddedViewId, selection, it) }
        }

        return results
            .distinctUntilChanged()
            .shareIn(scope, replay = 1, started = WhileSubscribed())
    }

    /**
     * Applies a non-AI selection to the pending list.
     *
     * @param embeddedViewId The embedded view being selected for.
     * @param selection The selection to apply. An AI selection resolves through its fallback,
     * which is how [resolveAi] hands off when the model has no answer.
     * @param pendingList The pending requests.
     * @return What to display, and the full list.
     */
    private fun select(
        embeddedViewId: String,
        selection: AirshipEmbeddedSelection,
        pendingList: List<EmbeddedDisplayRequest>
    ): EmbeddedDisplayRequestResult {
        return when (selection) {
            is AirshipEmbeddedSelection.ByComparator -> {
                val sorted = pendingList
                    .map { request -> request.embeddedInfo to request }
                    .sortedWith { a, b -> selection.comparator.compare(a.first, b.first) }
                    .map { it.second }
                EmbeddedDisplayRequestResult(next = sorted.firstOrNull(), list = sorted)
            }

            is AirshipEmbeddedSelection.ByInstanceId -> {
                // An allow-list in preference order: pending content not named is excluded
                // entirely rather than ordered last, so the list is the named subset rather
                // than everything pending.
                val byId = pendingList.associateBy { it.viewInstanceId }
                // Deduped: an app-supplied preference list concatenated from several sources
                // can name the same instance twice, and a repeat would put one request in the
                // list twice, which a pager keyed on instance ID rejects.
                val ordered = selection.instanceIds.distinct().mapNotNull { byId[it] }
                EmbeddedDisplayRequestResult(next = ordered.firstOrNull(), list = ordered)
            }

            AirshipEmbeddedSelection.Priority -> {
                // Read-only: what is sticky is decided by what actually reached the screen, via
                // recordDisplayed. Choosing here would let a surface that never displays an
                // instance — a group, a carousel, a view whose pending list excludes the sticky
                // one — rewrite what a sibling view is looking at.
                val lastId = lastViewedLock.withLock { lastViewed[embeddedViewId] }
                val current = lastId?.let { id -> pendingList.find { it.viewInstanceId == id } }
                    ?: pendingList.minByOrNull { it.priority }
                EmbeddedDisplayRequestResult(next = current, list = pendingList)
            }

            is AirshipEmbeddedSelection.ByAi ->
                select(embeddedViewId, selection.fallback.asSelection, pendingList)
        }
    }

    /**
     * The display results for an AI selection, ranking the pending list with the model.
     *
     * Stateful, unlike the other selections: what the model decided has to outlive the
     * emission it was decided on, so that a later change to the pending list can drop a
     * departed instance without re-asking, and so an instance already on screen can stay
     * there.
     *
     * The caller owns that state, because it has to outlive this flow too — a view that
     * detaches and reattaches collects again, and a session made here would blank the view and
     * re-rank every time. Falling back to a fresh one keeps a caller that has no view to speak
     * of, such as a test, working unchanged.
     *
     * @param embeddedViewId The embedded view being selected for.
     * @param selection The AI selection, carrying the config and the fallback.
     * @param pendingForView This view's pending requests.
     * @param session What to remember across collections, or null for collection-scoped state.
     * @return What to display, and the list in the order the model chose.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun aiDisplayRequests(
        embeddedViewId: String,
        selection: AirshipEmbeddedSelection.ByAi,
        pendingForView: Flow<List<EmbeddedDisplayRequest>>,
        session: EmbeddedSelectionSession?
    ): Flow<EmbeddedDisplayRequestResult> = flow {
        val state = session ?: EmbeddedSelectionSession()

        emitAll(
            // transformLatest cancels an in-flight ranking when the pending list changes
            // under it, so a decision about a set that no longer exists never lands.
            pendingForView.transformLatest { pendingList ->
                val incomingIds = pendingList.mapTo(mutableSetOf()) { it.viewInstanceId }
                val hasNewId = !state.knownIds.containsAll(incomingIds)
                state.knownIds = incomingIds

                // Ranking a single candidate is a round trip with one possible answer.
                if (pendingList.size < 2) {
                    state.phase = EmbeddedSelectionSession.Phase.Fallback
                    emit(resolveAi(embeddedViewId, selection, pendingList, state))
                    return@transformLatest
                }

                // Nothing arrived, so the set the model already ranked only shrank. The order
                // still has to drop what left, but there is nothing new to ask about — unless
                // this very change cancelled a ranking, since transformLatest tears the
                // in-flight one down and nothing else would ever answer the placeholder.
                if (!hasNewId && state.phase != EmbeddedSelectionSession.Phase.Resolving) {
                    emit(resolveAi(embeddedViewId, selection, pendingList, state))
                    return@transformLatest
                }

                // Before the placeholder, not after: `rank` returns synchronously when there
                // is no model, so emitting a blank frame first would be a flash that no other
                // selection has.
                val selector = aiSelector?.takeIf { it.isAvailable }
                if (selector == null) {
                    state.phase = EmbeddedSelectionSession.Phase.Fallback
                    emit(resolveAi(embeddedViewId, selection, pendingList, state))
                    return@transformLatest
                }

                state.phase = EmbeddedSelectionSession.Phase.Resolving
                emit(resolveAi(embeddedViewId, selection, pendingList, state))

                val ranking = selector.rank(selectionRequest(embeddedViewId, selection, pendingList))
                state.phase = ranking
                    ?.let(EmbeddedSelectionSession.Phase::Ranked)
                    ?: EmbeddedSelectionSession.Phase.Fallback
                emit(resolveAi(embeddedViewId, selection, pendingList, state))
            }
        )
    }

    /**
     * Turns the current AI state into what to display.
     *
     * @param embeddedViewId The embedded view being selected for.
     * @param selection The AI selection, carrying the config and the fallback.
     * @param pendingList The pending requests.
     * @param state The AI state, whose committed order this updates.
     * @return What to display, and the list in the order it settled on.
     */
    private fun resolveAi(
        embeddedViewId: String,
        selection: AirshipEmbeddedSelection.ByAi,
        pendingList: List<EmbeddedDisplayRequest>,
        state: EmbeddedSelectionSession
    ): EmbeddedDisplayRequestResult {
        val byId = pendingList.associateBy { it.viewInstanceId }

        val order = when (val phase = state.phase) {
            is EmbeddedSelectionSession.Phase.Ranked -> {
                val ranked = phase.ranking.filter(byId::containsKey)
                val committed = state.committedOrder.filter(byId::containsKey)
                if (!selection.config.allowDisplayInterruptions && committed.isNotEmpty()) {
                    // What is on screen stays where it is. Anything newly ranked was not up
                    // before, so it is an arrival rather than a reshuffle and can join at the
                    // end.
                    committed + ranked.filterNot(committed::contains)
                } else {
                    ranked
                }
            }

            // Hold the screen through a re-ask rather than blanking it. Empty when nothing is
            // up yet, which is what puts the placeholder on screen for the first ranking.
            EmbeddedSelectionSession.Phase.Resolving -> state.committedOrder.filter(byId::containsKey)

            EmbeddedSelectionSession.Phase.Fallback -> emptyList()
        }

        if (order.isNotEmpty()) {
            state.committedOrder = order
            val ordered = order.mapNotNull(byId::get)
            return EmbeddedDisplayRequestResult(next = ordered.firstOrNull(), list = ordered)
        }

        state.committedOrder = emptyList()
        return if (state.phase == EmbeddedSelectionSession.Phase.Resolving) {
            // The list is empty, not merely unselected: a group or carousel renders `list`, and
            // showing every candidate in arrival order until the ranking lands would page
            // content the model may be about to reorder or exclude.
            EmbeddedDisplayRequestResult(next = null, list = emptyList())
        } else {
            select(embeddedViewId, selection.fallback.asSelection, pendingList)
        }
    }

    /**
     * The ranking request for a pending list.
     *
     * @param embeddedViewId The embedded view being selected for.
     * @param selection The AI selection, carrying the config.
     * @param pendingList The pending requests.
     * @return The request.
     */
    private fun selectionRequest(
        embeddedViewId: String,
        selection: AirshipEmbeddedSelection.ByAi,
        pendingList: List<EmbeddedDisplayRequest>
    ): EmbeddedSelectionRequest = EmbeddedSelectionRequest(
        embeddedId = embeddedViewId,
        prompt = selection.config.prompt,
        candidates = pendingList.map { it.embeddedInfo },
        strategy = when (selection.config.strategy) {
            AirshipEmbeddedSelection.ByAi.Strategy.SCORE_THEN_PRIORITY ->
                EmbeddedSelectionStrategy.SCORE_THEN_PRIORITY
            AirshipEmbeddedSelection.ByAi.Strategy.PRIORITY_THEN_SCORE ->
                EmbeddedSelectionStrategy.PRIORITY_THEN_SCORE
        },
        minScoreThreshold = selection.config.minScoreThreshold,
        subjectHints = selection.config.subjectHints
    )
}

/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model

import com.urbanairship.UALog
import com.urbanairship.android.layout.environment.ThomasState
import com.urbanairship.android.layout.property.PageBranching
import com.urbanairship.android.layout.property.PagerControllerBranching
import com.urbanairship.android.layout.property.StateAction
import com.urbanairship.json.JsonSerializable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class PagerBranchControl(
    private val availablePages: List<PagerModel.Item>,
    private val controllerBranching: PagerControllerBranching,
    private val thomasState: StateFlow<ThomasState>,
    private val onBranchUpdated: (List<PagerModel.Item>, Boolean) -> Unit,
    private val actionsRunner: (List<StateAction>) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
) {

    private val history = mutableListOf<PagerModel.Item>()

    private val _isComplete = MutableStateFlow(false)
    val isComplete = _isComplete.asStateFlow()

    init {
        scope.launch {
            thomasState.collect(::updateState)
        }
    }

    private fun updateState(state: ThomasState) {
        // Re-evaluate the path based on the current payload
        if (history.isEmpty() && availablePages.isNotEmpty()) {
            history.add(availablePages.first())
        }

        UALog.v { "ThomasPager BranchControl updateState: history=${history.map { it.identifier }}" }

        // Evaluate completion
        val runCompletedStateActions = if (!_isComplete.value) {
            val matched = controllerBranching.completions
                .firstOrNull { it.predicate?.apply(state) != false }

            val completed = matched != null

            _isComplete.update { completed }

            completed
        } else {
            // We're already complete, no need to run actions again
            false
        }

        // Notify branch updated. Deduplicate the prefix against the suffix so that
        // pages targeted by branching (e.g. language toggles) don't appear twice when
        // the branching path leads back to a page already visited in the history.
        val prefix = history.dropLast(1)
        val suffix = buildPathFrom(history.last(), state)
        val suffixIds = suffix.mapTo(mutableSetOf()) { it.identifier }
        val deduplicatedPrefix = prefix.filter { it.identifier !in suffixIds }
        val result = deduplicatedPrefix + suffix
        UALog.v { "ThomasPager BranchControl updateState: prefix=${prefix.map { it.identifier }} suffix=${suffix.map { it.identifier }} result=${result.map { it.identifier }}" }
        onBranchUpdated(result, _isComplete.value)

        // Run completion state actions if we just completed
        if (runCompletedStateActions) {
            controllerBranching.completions
                .filter { it.predicate?.apply(state) != false }
                .mapNotNull { it.stateActions }
                .flatten()
                .run(actionsRunner)
        }
    }

    fun addToHistory(id: String) {
        clearHistoryAfter(id)
        val page = availablePages.firstOrNull { it.identifier == id } ?: return
        if (history.contains(page)) {
            UALog.v { "ThomasPager BranchControl addToHistory($id): already in history, skipping" }
            return
        }

        history.add(page)
        UALog.v { "ThomasPager BranchControl addToHistory($id): history=${history.map { it.identifier }}" }
    }

    /**
     * Ensures the page is in history without clearing anything after it.
     * Used by resolve(NEXT) to prevent rapid button presses from skipping
     * intermediate pages, without the destructive side effects of addToHistory.
     */
    fun ensureInHistory(id: String) {
        val page = availablePages.firstOrNull { it.identifier == id } ?: return
        if (history.contains(page)) return
        history.add(page)
        UALog.v { "ThomasPager BranchControl ensureInHistory($id): history=${history.map { it.identifier }}" }
    }

    fun removeFromHistory(id: String) {
        scope.launch {
            val page = availablePages.firstOrNull { it.identifier == id } ?: return@launch
            history.remove(page)
            UALog.v { "ThomasPager BranchControl removeFromHistory($id): history=${history.map { it.identifier }}" }
        }
    }

    private fun clearHistoryAfter(id: String) {
        val index = history.indexOfFirst { it.identifier == id } + 1
        if (index < 1 || index >= history.size) {
            return
        }

        history.subList(index, history.size).clear()
    }

    fun onPageRequest(request: PageRequest) {
        when (request) {
            PageRequest.NEXT -> {}
            PageRequest.FIRST -> {
                history.clear()
            }
            PageRequest.BACK -> {
                history.removeLastOrNull()
            }
        }
        UALog.v { "ThomasPager BranchControl onPageRequest($request): history=${history.map { it.identifier }}" }
    }

    private fun buildPathFrom(page: PagerModel.Item, payload: JsonSerializable): List<PagerModel.Item> {
        var pageIndex = availablePages.indexOf(page)
        if (pageIndex < 0) { return emptyList() }

        val result = mutableListOf<PagerModel.Item>()
        while (pageIndex >= 0 && pageIndex < availablePages.size) {
            val current = availablePages[pageIndex]
            if (result.contains(current)) {
                UALog.w { "Trying to add a duplicate $current" }
                break
            }

            result.add(current)

            val branching = current.branching ?: break
            val nextPage = branching.nextPageId(payload) ?: break
            pageIndex = availablePages.indexOfFirst { it.identifier == nextPage }
        }

        return result.toList()
    }
}

private fun PageBranching.nextPageId(
    payload: JsonSerializable,
): String? {
    return nextPageSelectors
        ?.firstOrNull { it.predicate?.apply(payload) != false }
        ?.pageId
}

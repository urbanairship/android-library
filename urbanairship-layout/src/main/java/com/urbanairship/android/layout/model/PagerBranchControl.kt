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
    private val onBranchUpdated: (List<PagerModel.Item>) -> Unit,
    private val actionsRunner: (List<StateAction>) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
) {

    private val history = mutableListOf<PagerModel.Item>()

    private val _isComplete = MutableStateFlow(false)
    val isComplete = _isComplete.asStateFlow()

    init {
        scope.launch {
            thomasState.collect {
                updateState()
            }
        }
    }

    private fun updateState() {
        val payload = thomasState.value

        reEvaluatePath(payload)
        evaluateCompletion(payload)
    }

    fun addToHistory(id: String) {
        scope.launch {
            clearHistoryAfter(id)
            val page = availablePages.firstOrNull { it.identifier == id } ?: return@launch
            if (history.contains(page)) {
                return@launch
            }

            history.add(page)
        }
    }

    fun removeFromHistory(id: String) {
        scope.launch {
            val page = availablePages.firstOrNull { it.identifier == id } ?: return@launch
            history.remove(page)
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
    }

    private fun reEvaluatePath(payload: JsonSerializable) {
        if (history.isEmpty() && availablePages.isNotEmpty()) {
            history.add(availablePages.first())
        }

        onBranchUpdated(history.dropLast(1) + buildPathFrom(history.last(), payload))
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

    private fun evaluateCompletion(payload: JsonSerializable) {
        if (_isComplete.value) {
            return //once complete always complete
        }

        val matched = controllerBranching.completions
            .firstOrNull { it.predicate?.apply(payload) != false }

        val completed = matched != null

        if (completed && !_isComplete.value) {
            performCompletionStateActions(payload)
        }

        _isComplete.update { completed }
    }

    private fun performCompletionStateActions(payload: JsonSerializable) {
        controllerBranching.completions
            .filter { it.predicate?.apply(payload) != false }
            .mapNotNull { it.stateActions }
            .flatten()
            .run(actionsRunner)
    }
}

private fun PageBranching.nextPageId(payload: JsonSerializable): String? {
    return nextPageSelectors
        ?.firstOrNull { it.predicate?.apply(payload) != false }
        ?.pageId
}

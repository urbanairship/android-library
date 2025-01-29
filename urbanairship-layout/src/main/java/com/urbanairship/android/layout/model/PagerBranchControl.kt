/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model

import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.property.PageBranching
import com.urbanairship.android.layout.property.PagerControllerBranching
import com.urbanairship.android.layout.property.StateAction
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class PagerBranchControl(
    private val availablePages: List<PagerModel.Item>,
    private val controllerBranching: PagerControllerBranching,
    private val viewState: SharedState<State.Layout>?,
    private val formState: SharedState<State.Form>?,
    private val actionsRunner: (List<StateAction>) -> Unit,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher()
) {

    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val history = mutableListOf<PagerModel.Item>()

    private val _pages = MutableStateFlow(listOf<PagerModel.Item>())
    val pages = _pages.asStateFlow()

    private val _canGoBackState = MutableStateFlow(true)
    val canGoBack = _canGoBackState.asStateFlow()

    private val _isComplete = MutableStateFlow(false)
    val isComplete = _isComplete.asStateFlow()

    init {
        scope.launch { listenForUpdates() }
        //TODO: call updateState() ??
    }

    private suspend fun listenForUpdates() {
        val stateFlow = viewState?.changes ?: return
        val formFlow = formState?.changes ?: return

        combine(stateFlow, formFlow) { state, form -> Pair(state, form) }
            .distinctUntilChanged()
            .collect { updateState() }
    }

    private fun updateState() {
        val payload = generatePayload() ?: return

        reEvaluatePath(payload)
        evaluateCompletion(payload)
        updateCanGoBack()
    }

    fun addToHistory(id: String) {
        val page = availablePages.firstOrNull { it.identifier == id } ?: return
        if (history.contains(page)) {
            return
        }

        history.add(page)
        updateCanGoBack()
    }

    fun clearHistoryAfter(id: String) {
        val index = history.indexOfFirst { it.identifier == id }
        if (index < 0) {
            return
        }

        //TODO: test
        history.subList(index, history.size).clear()
        updateCanGoBack()
    }

    fun resolve(request: PageRequest): Boolean {
        updateState()

        when (request) {
            PageRequest.NEXT -> return true
            PageRequest.FIRST -> {
                history.clear()
                return true
            }
            PageRequest.BACK -> {
                if (canGoBack.value) {
                    history.removeLastOrNull()
                    return true
                } else {
                    return false
                }
            }
        }
    }

    private fun updateCanGoBack() {
        val payload = generatePayload() ?: return

        _canGoBackState.update {
            val result = history.lastOrNull() ?: return@update false
            result.branching?.canGoBack(payload) != false
        }
    }

    private fun generatePayload(): JsonSerializable? {
        val viewState = viewState?.value?.state ?: return null
//        val formState = formState?.value?.data?.values?.firstOrNull() ?: return null
        val formState = formState?.value?.data?.values?.firstOrNull() ?: FormData.Form(
            identifier = "current",
            responseType = null,
            children = emptySet()
        )

        return viewState
            .toMutableMap()
            .apply {
                put("\$forms", jsonMapOf(
                    "current" to formState.formData.toJsonValue()).toJsonValue()
                )
            }
            .let(JsonValue::wrap)
    }

    private fun reEvaluatePath(payload: JsonSerializable) {
        if (history.isEmpty() && availablePages.isNotEmpty()) {
            history.add(availablePages.first())
        }

        _pages.update { history.dropLast(1) + buildPathFrom(history.last(), payload) }
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

private fun PageBranching.canGoBack(payload: JsonSerializable): Boolean {
    val control = previousPageControl ?: return true
    if (control.alwaysDisabled == true) {
        return false
    }

    return control.predicate?.apply(payload) != true
}
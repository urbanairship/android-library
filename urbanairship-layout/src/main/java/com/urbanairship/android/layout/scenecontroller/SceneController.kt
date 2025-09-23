package com.urbanairship.android.layout.scenecontroller

import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.model.PageRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Exposes Scene controls to a custom view.
 */
public class SceneController internal constructor(
    pagerState: SharedState<State.Pager>? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val dismiss: (cancelFutureDisplays: Boolean) -> Unit,
) {

    public val pager: PagerController = PagerController(pagerState, dispatcher)

    /**
     * Dismisses the current scene.
     *
     * @param cancelFutureDisplays A Boolean value that, if `true`, should cancel any scheduled
     * or future displays related to this scene.
     *
     * */
    public fun dismiss(cancelFutureDisplays: Boolean = false) {
        dismiss.invoke(cancelFutureDisplays)
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {
        public fun empty(): SceneController = SceneController {}
    }
}

/** Pager controller class for external control over pager navigation. */
public class PagerController internal constructor(
    private val pagerState: SharedState<State.Pager>? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
) {

    private val _state = MutableStateFlow(
        State(
            canGoBack = pagerState?.changes?.value?.hasPrevious == true,
            canGoNext = pagerState?.changes?.value?.hasNext == true
        )
    )
    public val state: StateFlow<PagerController.State> = _state.asStateFlow()
    private val scope = CoroutineScope(dispatcher)

    init {
        scope.launch {
            pagerState?.changes?.collect { state ->
                _state.update {
                    State(
                        canGoBack = state.hasPrevious,
                        canGoNext = state.hasNext
                    )
                }
            }
        }
    }

    public class State(
        /** A Boolean value that indicates whether it is possible to navigate back. */
        public val canGoBack: Boolean = false,

        /** A Boolean value that indicates whether it is possible to navigate forward. */
        public val canGoNext: Boolean = false,
    )

    /**
     * Attempts to navigate based on the specified request.
     *
     * @param request: The navigation request, either [NavigationRequest.NEXT] or [NavigationRequest.BACK].
     * @return A Boolean value indicating whether the navigation was successful.
     */
    public fun navigate(request: NavigationRequest): Boolean {
        if (pagerState == null) return false

        val pageRequest = when(request) {
            NavigationRequest.NEXT -> PageRequest.NEXT
            NavigationRequest.BACK -> PageRequest.BACK
        }

        var result = false

        pagerState.update { current ->
            val newState = current.copyWithPageRequest(pageRequest)
            result = newState.pageIndex != current.pageIndex
            newState
        }

        return result
    }

    public enum class NavigationRequest {
        /** A request to navigate to the next scene. */
        NEXT,
        /** A request to navigate to the previous scene. */
        BACK
    }
}

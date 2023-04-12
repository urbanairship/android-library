package com.urbanairship.android.layout.environment

import com.urbanairship.android.layout.util.DelicateLayoutApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Wraps a generic type, exposed via a `StateFlow` of updates and an atomic update method. */
internal class SharedState<T>(initialValue: T) {
    private val stateFlow = MutableStateFlow(initialValue)
    val changes: StateFlow<T> = stateFlow.asStateFlow()

    /**
     * The current state value.
     *
     * State should be collected via `changes` instead of `value` in nearly all cases.
     * This method is exposed for limited use where the state is only read once, without the
     * need to react to further changes. Care should be taken to ensure that any data read
     * from the current `value` is not stale by the time it is used.
     */
    @DelicateLayoutApi
    val value: T
        get() = stateFlow.value

    fun update(block: (T) -> T) = stateFlow.update { state ->
        block(state)
    }
}

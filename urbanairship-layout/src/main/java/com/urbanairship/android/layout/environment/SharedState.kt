package com.urbanairship.android.layout.environment

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Wraps a generic type, exposed via a `StateFlow` of updates and an atomic update method. */
internal class SharedState<T>(initialValue: T) {
    private val stateFlow = MutableStateFlow(initialValue)
    val changes: StateFlow<T> = stateFlow.asStateFlow()
    val value: T = stateFlow.value

    fun update(block: (T) -> T) = stateFlow.update { state ->
        block(state)
    }
}

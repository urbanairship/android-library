/* Copyright Airship and Contributors */

package com.urbanairship.util

import androidx.annotation.RestrictTo
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * https://github.com/Kotlin/kotlinx.coroutines/issues/2631
 */

/**
 * @hide
 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DerivedStateFlow<T>(
    private val onValue: () -> T,
    private val updates: Flow<T>
) : StateFlow<T> {

    /// Same as kotlin's state flow implementation
    override val replayCache: List<T>
        get() = listOf(value)

    override val value: T
        get() = onValue()

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        coroutineScope {
            updates.distinctUntilChanged().stateIn(this).collect(collector)
        }
    }
}

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun <T1, T2, TR> combineStates(
    flow1: StateFlow<T1>,
    flow2: StateFlow<T2>,
    transform: (t1: T1, t2: T2) -> TR
): StateFlow<TR> {
    return DerivedStateFlow(
        onValue = { transform(flow1.value, flow2.value) },
        updates = combine(flow1, flow2, transform)
    )
}

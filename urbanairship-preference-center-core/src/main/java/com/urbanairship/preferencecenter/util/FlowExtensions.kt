package com.urbanairship.preferencecenter.util

import androidx.annotation.RestrictTo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

/**
 * Folds the given flow with operation, emitting every intermediate result, including initial value as a flattened
 * sequence of emissions.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun <T, R> Flow<T>.airshipScanConcat(
    initial: R,
    transform: suspend (accumulator: R, value: T) -> Flow<R>
): Flow<R> = flow {
    var accumulator = initial
    emit(accumulator)
    collect { value ->
        transform(accumulator, value).collect { transformed ->
            accumulator = transformed
            emit(accumulator)
        }
    }
}

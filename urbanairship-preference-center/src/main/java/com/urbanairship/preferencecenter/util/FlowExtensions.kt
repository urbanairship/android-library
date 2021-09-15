package com.urbanairship.preferencecenter.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

/**
 * Folds the given flow with operation, emitting every intermediate result, including initial value as a flattened
 * sequence of emissions.
 */
internal fun <T, R> Flow<T>.scanConcat(
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

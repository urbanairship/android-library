package com.urbanairship.preferencecenter.util

import androidx.annotation.RestrictTo
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/** How long to wait for contact data before giving up. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public val CONTACT_DATA_TIMEOUT: Duration = 30.seconds

/**
 * Fails if the flow does not produce a first value within [timeout]. Later gaps are allowed.
 *
 * Contact data flows emit nothing until the contact resolves, so a combine of them can otherwise
 * wait forever.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun <T> Flow<T>.airshipFailIfSlowToStart(
    timeout: Duration,
    message: String
): Flow<T> = flow {
    val started = AtomicBoolean(false)

    coroutineScope {
        val watchdog = launch {
            delay(timeout)
            if (!started.get()) {
                throw TimeoutException(message)
            }
        }

        collect { value ->
            started.set(true)
            watchdog.cancel()
            emit(value)
        }

        watchdog.cancel()
    }
}

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

/* Copyright Airship and Contributors */
package com.urbanairship.util

import androidx.annotation.RestrictTo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs suspending blocks completely in FIFO order.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SerialQueue {

    private val mutex = Mutex()

    /**
     * Executes the [operation] sequentially. If another operation is currently running,
     * this suspends until the previous operation finishes.
     *
     * Naturally handles exceptions and coroutine cancellation without deadlocking.
     */
    public suspend fun <T> run(operation: suspend () -> T): T {
        return mutex.withLock {
            operation()
        }
    }
}

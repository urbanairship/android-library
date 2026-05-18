/* Copyright Airship and Contributors */
package com.urbanairship.util

import androidx.annotation.RestrictTo
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * Serializes async operations in caller-observed submission order. Each enqueued block runs
 * only after every previously-enqueued block has completed.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AsyncSerialQueue(private val scope: CoroutineScope) {

    private val lock = ReentrantLock()
    private var lastOp: Job? = null

    /**
     * Appends [block] to the chain and returns immediately. [block] runs after the prior
     * tail completes.
     */
    public fun enqueue(block: suspend () -> Unit) {
        lock.withLock {
            val previous = lastOp
            lastOp = scope.launch {
                previous?.join()
                block()
            }
        }
    }

    /**
     * Appends [block] to the chain and suspends until it completes, returning its result.
     * If the underlying [scope] is cancelled before [block] runs, `await()` throws
     * [kotlinx.coroutines.CancellationException] instead of hanging.
     */
    public suspend fun <T> enqueueAndAwait(block: suspend () -> T): T {
        val deferred = lock.withLock {
            val previous = lastOp
            scope.async {
                previous?.join()
                block()
            }.also { lastOp = it }
        }
        return deferred.await()
    }
}

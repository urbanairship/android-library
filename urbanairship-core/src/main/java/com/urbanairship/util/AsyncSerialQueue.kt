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
 * Prevents nested receivers from leaking across [AsyncSerialQueue] blocks.
 *
 * @hide
 */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
public annotation class AsyncSerialQueueDsl

/**
 * Receiver scope for operations that must run inside an [AsyncSerialQueue] block.
 * Helpers declared as extensions on this type can only be called from within
 * [AsyncSerialQueue.enqueue] / [AsyncSerialQueue.enqueueAndAwait], which the compiler
 * enforces.
 *
 * @hide
 */
@AsyncSerialQueueDsl
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AsyncSerialQueueScope

private object AsyncSerialQueueScopeImpl : AsyncSerialQueueScope

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
    public fun enqueue(block: suspend AsyncSerialQueueScope.() -> Unit) {
        lock.withLock {
            val previous = lastOp
            lastOp = scope.launch {
                previous?.join()
                AsyncSerialQueueScopeImpl.block()
            }
        }
    }

    /**
     * Appends [block] to the chain and suspends until it completes, returning its result.
     * If the underlying [scope] is cancelled before [block] runs, `await()` throws
     * [kotlinx.coroutines.CancellationException] instead of hanging.
     */
    public suspend fun <T> enqueueAndAwait(block: suspend AsyncSerialQueueScope.() -> T): T {
        val deferred = lock.withLock {
            val previous = lastOp
            scope.async {
                previous?.join()
                AsyncSerialQueueScopeImpl.block()
            }.also { lastOp = it }
        }
        return deferred.await()
    }
}

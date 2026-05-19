/* Copyright Airship and Contributors */
package com.urbanairship.util

import androidx.annotation.RestrictTo
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

/**
 * Prevents nested receivers from leaking across [AsyncSerialQueue] blocks.
 *
 * @hide
 */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
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
     * tail completes. Non-cancellation throws are suppressed to keep the chain and the
     * underlying [scope] alive.
     */
    public fun enqueue(block: suspend AsyncSerialQueueScope.() -> Unit) {
        lock.withLock {
            var previous = lastOp
            lastOp = scope.launch {
                previous?.join()
                previous = null
                runLink { block() }
            }
        }
    }

    /**
     * Appends [block] to the chain and suspends until it completes, returning its result.
     * Wraps the block in [Result] so failures surface through [kotlinx.coroutines.Deferred.await] without
     * cancelling the underlying [scope].
     */
    public suspend fun <T> enqueueAndAwait(block: suspend AsyncSerialQueueScope.() -> T): T {
        val deferred = lock.withLock {
            var previous = lastOp
            scope.async {
                previous?.join()
                previous = null
                runLink { block() }
            }.also { lastOp = it }
        }
        return deferred.await().getOrThrow()
    }

    /**
     * Runs [block] in the queue's receiver scope and returns the outcome as a [Result].
     * [CancellationException] is rethrown so coroutine cancellation propagates normally
     * instead of being captured. After the block finishes, clears [lastOp] if this coroutine
     * is still the tail so an idle queue doesn't retain its last completed Job/Deferred.
     */
    private suspend inline fun <T> runLink(
        crossinline block: suspend AsyncSerialQueueScope.() -> T
    ): Result<T> = try {
        runCatching { AsyncSerialQueueScopeImpl.block() }
            .onFailure { if (it is CancellationException) throw it }
    } finally {
        val self = currentCoroutineContext()[Job]
        lock.withLock { if (lastOp === self) lastOp = null }
    }
}

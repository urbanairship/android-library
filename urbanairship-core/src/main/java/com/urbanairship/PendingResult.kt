/* Copyright Airship and Contributors */
package com.urbanairship

import android.os.Looper
import androidx.annotation.RestrictTo
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * A pending result.
 *
 * @param <T> Type of result.
</T> */
public class PendingResult<T> public constructor() : Cancelable, Future<T?> {

    private var isCanceled = false
    private var resultSet = false
    private var runCallbacks = true

    private var result: T? = null

    private val cancelables = mutableListOf<Cancelable>()
    private val resultCallbacks = mutableListOf<CancelableOperation>()

    override fun cancel(): Boolean {
        return cancel(false)
    }

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        synchronized(this) {
            if (isCancelled) {
                return true
            }
            runCallbacks = false

            // Cancel any callbacks just in-case they are still pending execution
            for (pendingCallback in resultCallbacks) {
                pendingCallback.cancel(mayInterruptIfRunning)
            }

            resultCallbacks.clear()

            if (isDone) {
                return false
            }

            isCanceled = true

            (this as Object).notifyAll()

            for (cancelable in cancelables) {
                cancelable.cancel(mayInterruptIfRunning)
            }

            cancelables.clear()
            return true
        }
    }

    /**
     * Sets the pending result.
     *
     * @param result The pending result.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun setResult(result: T?) {
        synchronized(this) {
            if (isDone) {
                return
            }
            this.result = result
            this.resultSet = true
            cancelables.clear()

            (this as Object).notifyAll()

            for (callback in resultCallbacks) {
                callback.run()
            }
            resultCallbacks.clear()
        }
    }

    /**
     * Returns the result if set.
     *
     * @return The result if set, otherwise `null`.
     */
    public fun getResult(): T? {
        synchronized(this) {
            return result
        }
    }

    override fun isCancelled(): Boolean {
        synchronized(this) { return isCanceled }
    }

    override fun isDone(): Boolean {
        synchronized(this) { return isCanceled || resultSet }
    }

    @Throws(InterruptedException::class, ExecutionException::class)
    override fun get(): T? {
        synchronized(this) {
            if (isDone) {
                return result
            }
            (this as Object).wait()
            return result
        }
    }

    @Throws(
        InterruptedException::class,
        ExecutionException::class,
        TimeoutException::class
    )
    override fun get(l: Long, timeUnit: TimeUnit): T? {
        synchronized(this) {
            if (isDone) {
                return result
            }
            (this as Object).wait(timeUnit.toMillis(l))
            return result
        }
    }

    /**
     * Adds a [Cancelable] that will be called when
     * the pending result is canceled. If the pending result is already canceled the operation
     * will immediately be canceled.
     *
     * @param cancelable The instance that implements the [Cancelable] interface.
     */
    public fun addCancelable(cancelable: Cancelable): PendingResult<T> {
        synchronized(this) {
            if (isCancelled) {
                cancelable.cancel()
            }
            if (!isDone) {
                cancelables.add(cancelable)
            }
        }

        return this
    }

    /**
     * Adds a result callback.
     *
     * @param resultCallback The result callback.
     * @return The pending result.
     */
    public fun addResultCallback(resultCallback: ResultCallback<T>): PendingResult<T> {
        return addResultCallback(Looper.myLooper(), resultCallback)
    }

    /**
     * Adds a result callback.
     *
     * @param looper The looper to run the callback on.
     * @param resultCallback The result callback.
     * @return The pending result.
     */
    public fun addResultCallback(
        looper: Looper?, resultCallback: ResultCallback<T>
    ): PendingResult<T> {
        synchronized(this) {
            if (isCancelled || !runCallbacks) {
                return this
            }
            val pendingCallback: CancelableOperation = object : CancelableOperation(looper) {
                override fun onRun() {
                    synchronized(this@PendingResult) {
                        if (runCallbacks) {
                            resultCallback.onResult(result)
                        }
                    }
                }
            }

            if (isDone) {
                pendingCallback.run()
            }
            resultCallbacks.add(pendingCallback)
        }

        return this
    }
}

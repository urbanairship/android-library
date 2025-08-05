/* Copyright Airship and Contributors */
package com.urbanairship

import android.os.Handler
import android.os.Looper

/**
 * A cancelable operation that executes its task on a specific looper.
 */
public open class CancelableOperation @JvmOverloads public constructor(
    looper: Looper? = null
) : Cancelable, Runnable {

    private var isFinished = false
    private var isRunning = false
    private var isCanceled = false

    /**
     * Gets the handler for the operation.
     */
    public val handler: Handler
    private val internalRunnable: Runnable

    private val cancelables = mutableListOf<Cancelable>()
    private val runnables = mutableListOf<Runnable>()

    /**
     * CancelableOperation constructor.
     *
     * @param looper A Looper object whose message queue will be used for the callback,
     * or null to make callbacks on the calling thread or main thread if the current thread
     * does not have a looper associated with it.
     */
    /**
     * CancelableOperation constructor.
     */
    init {
        this.handler = looper?.let { Handler(it) }
            ?: Looper.myLooper()?.let { Handler(it) }
            ?: Handler(Looper.getMainLooper())

        internalRunnable = Runnable {
            synchronized(this@CancelableOperation) {
                if (isDone()) {
                    return@Runnable
                }
                onRun()
                isFinished = true

                for (runnable in runnables) {
                    runnable.run()
                }

                cancelables.clear()
                runnables.clear()
            }
        }
    }

    override fun cancel(): Boolean {
        return cancel(false)
    }

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        synchronized(this) {
            if (isDone()) {
                return false
            }

            isCanceled = true
            handler.removeCallbacks(internalRunnable)
            handler.post { onCancel() }

            for (cancelable in cancelables) {
                cancelable.cancel(mayInterruptIfRunning)
            }

            cancelables.clear()
            runnables.clear()

            return true
        }
    }

    override fun run() {
        synchronized(this) {
            if (isDone() || isRunning) {
                return
            }
            isRunning = true
            handler.post(internalRunnable)
        }
    }

    override fun isDone(): Boolean {
        synchronized(this) {
            return isFinished || isCanceled
        }
    }


    override fun isCancelled(): Boolean {
        synchronized(this) {
            return isCanceled
        }
    }

    /**
     * Adds a runnable that will be called when operation is finished. If the operation is already
     * finished the runnable will be called immediately.
     *
     * @param runnable A runnable.
     */
    public fun addOnRun(runnable: Runnable): CancelableOperation {
        synchronized(this) {
            if (isFinished) {
                runnable.run()
            } else {
                runnables.add(runnable)
            }
        }

        return this
    }

    /**
     * Adds a [Cancelable] that will be called when operation is cancelled.  If the operation
     * is already canceled the operation will immediately be canceled.
     *
     * @param cancelable The instance that implements the [Cancelable] interface.
     */
    public fun addOnCancel(cancelable: Cancelable): CancelableOperation {
        synchronized(this) {
            if (isCancelled()) {
                cancelable.cancel()
            }
            if (!isDone()) {
                cancelables.add(cancelable)
            }
        }

        return this
    }

    /**
     * Called on the handlers callback when the operation is canceled.
     */
    protected open fun onCancel() { }

    /**
     * Called on the handlers callback when the operation is running.
     */
    protected open fun onRun() { }
}

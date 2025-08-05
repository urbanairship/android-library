/* Copyright Airship and Contributors */
package com.urbanairship

/**
 * Interface for an cancelable operation.
 */
public interface Cancelable {

    /**
     * Cancels the operation.
     *
     * @param mayInterruptIfRunning `true` if the thread executing this task should be interrupted; otherwise, in-progress tasks are allowed to complete.
     * @return `false` if the cancelable was able to be cancelled, otherwise `true`.
     */
    public fun cancel(mayInterruptIfRunning: Boolean): Boolean

    /**
     * Cancels the operation.
     *
     * @return `false` if the cancelable was able to be cancelled, otherwise `true`.
     */
    public fun cancel(): Boolean

    /**
     * Determines if the operation is canceled or completed.
     *
     * @return `true` if canceled or completed, otherwise `false`
     */
    public fun isDone(): Boolean

    /**
     * Determines if the operation is canceled.
     *
     * @return `true` if canceled, otherwise `false`
     */
    public fun isCancelled(): Boolean
}

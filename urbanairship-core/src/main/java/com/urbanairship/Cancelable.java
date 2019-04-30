/* Copyright Airship and Contributors */

package com.urbanairship;

/**
 * Interface for an cancelable operation.
 */
public interface Cancelable {

    /**
     * Cancels the operation.
     *
     * @param mayInterruptIfRunning {@code true} if the thread executing this task should be interrupted; otherwise, in-progress tasks are allowed to complete.
     * @return {@code false} if the cancelable was able to be cancelled, otherwise {@code true}.
     */
    boolean cancel(boolean mayInterruptIfRunning);

    /**
     * Cancels the operation.
     *
     * @return {@code false} if the cancelable was able to be cancelled, otherwise {@code true}.
     */
    boolean cancel();

    /**
     * Determines if the operation is canceled or completed.
     *
     * @return <code>true</code> if canceled or completed, otherwise <code>false</code>
     */
    boolean isDone();

    /**
     * Determines if the operation is canceled.
     *
     * @return <code>true</code> if canceled, otherwise <code>false</code>
     */
    boolean isCancelled();

}

/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

/**
 * Interface for an cancelable operation.
 */
public interface Cancelable {

    /**
     * Cancels the operation.
     */
    void cancel();

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
    boolean isCanceled();
}

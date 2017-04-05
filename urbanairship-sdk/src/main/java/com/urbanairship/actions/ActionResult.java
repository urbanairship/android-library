/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Stores the results of running an {@link com.urbanairship.actions.Action}.
 */
public final class ActionResult {

    @IntDef({STATUS_COMPLETED, STATUS_REJECTED_ARGUMENTS, STATUS_ACTION_NOT_FOUND, STATUS_EXECUTION_ERROR})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {}

    /**
     * The action accepted the arguments and executed without an exception.
     */
    public static final int STATUS_COMPLETED = 1;

    /**
     * The action was not performed because the arguments were rejected by
     * either the predicate in the registry or the action.
     */
    public static final int STATUS_REJECTED_ARGUMENTS = 2;

    /**
     * The action was not performed because the action was not found
     * in the {@link com.urbanairship.actions.ActionRegistry}. This value is
     * only possible if trying to run an action by name through the
     * {@link com.urbanairship.actions.ActionRunRequestFactory}.
     */
    public static final int STATUS_ACTION_NOT_FOUND = 3;

    /**
     * The action encountered a runtime exception during execution. The
     * exception field will contain the caught exception.
     */
    public static final int STATUS_EXECUTION_ERROR = 4;


    /**
     * The result exception. This value may be null.
     */
    private final Exception exception;

    /**
     * The result value.
     */
    private final ActionValue value;

    /**
     * Run status of the action.
     */
    private final @Status int status;

    /**
     * Factory method to create an empty result
     */
    @NonNull
    public static ActionResult newEmptyResult() {
        return new ActionResult(null, null, STATUS_COMPLETED);
    }

    /**
     * Factory method to create a result with a value
     *
     * @param value The result value
     */
    @NonNull
    public static ActionResult newResult(ActionValue value) {
        return new ActionResult(value, null, STATUS_COMPLETED);
    }

    /**
     * Factory method to create a result with an exception
     *
     * @param exception The result value
     */
    @NonNull
    public static ActionResult newErrorResult(Exception exception) {
        return new ActionResult(null, exception, STATUS_EXECUTION_ERROR);
    }

    /**
     * Factory method to create an empty result with a specific status
     *
     * @param status The result's status
     */
    @NonNull
    static ActionResult newEmptyResultWithStatus(@Status int status) {
        return new ActionResult(null, null, status);
    }

    /**
     * ActionResult constructor.
     *
     * @param value The result value.
     * @param exception The result exception.
     * @param status The run status of the action.
     */
    ActionResult(ActionValue value, Exception exception, @Status int status) {
        this.value = value == null ? new ActionValue() : value;
        this.exception = exception;
        this.status = status;
    }

    /**
     * Retrieves the result value.
     *
     * @return The result value as an ActionValue.
     */
    @NonNull
    public ActionValue getValue() {
        return value;
    }

    /**
     * Retrieves the results exception.
     *
     * @return The result exception.
     */
    @Nullable
    public Exception getException() { return exception; }

    /**
     * Retrieves the status of the action run.
     *
     * @return The status of the action run.
     */
    @Status
    public int getStatus() {
        return status;
    }
}

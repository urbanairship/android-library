/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.actions;

/**
 * Stores the results of running an {@link com.urbanairship.actions.Action}.
 */
public class ActionResult {

    /**
     * The run status of the action.
     */
    public enum Status {
        /**
         * The action accepted the arguments and executed without an exception.
         */
        COMPLETED,

        /**
         * The action was not performed because the arguments were rejected by
         * either the predicate in the registry or the action.
         */
        REJECTED_ARGUMENTS,

        /**
         * The action was not performed because the action was not found
         * in the {@link com.urbanairship.actions.ActionRegistry}. This value is
         * only possible if trying to run an action by name through the
         * {@link com.urbanairship.actions.ActionRunner}.
         */
        ACTION_NOT_FOUND,

        /**
         * The action encountered a runtime exception during execution.  The
         * exception field will contain the caught exception.
         */
        EXECUTION_ERROR
    }

    /**
     * The result exception. This value may be null.
     */
    private final Exception exception;

    /**
     * The result value. This value may be null.
     */
    private final Object value;

    /**
     * Run status of the action.
     */
    private final Status status;

    /**
     * Factory method to create an empty result
     */
    public static ActionResult newEmptyResult() {
        return new ActionResult(null, null, Status.COMPLETED);
    }

    /**
     * Factory method to create a result with a value
     *
     * @param value The result value
     */
    public static ActionResult newResult(Object value) {
        return new ActionResult(value, null, Status.COMPLETED);
    }

    /**
     * Factory method to create a result with an exception
     *
     * @param exception The result value
     */
    public static ActionResult newErrorResult(Exception exception) {
        return new ActionResult(null, exception, Status.EXECUTION_ERROR);
    }

    /**
     * Factory method to create an empty result with a specific status
     *
     * @param status The result's status
     */
    static ActionResult newEmptyResultWithStatus(Status status) {
        return new ActionResult(null, null, status);
    }

    /**
     * ActionResult constructor.
     *
     * @param value The result value
     * @param exception The result exception
     * @param status The run status of the action. A null status will be coerced
     * to Status.completed.
     */
    protected ActionResult(Object value, Exception exception, Status status) {
        this.value = value;
        this.exception = exception;
        this.status = status != null ? status : Status.COMPLETED;
    }

    /**
     * Retrieves the result value.
     *
     * @return The result value as an Object
     */
    public Object getValue() {
        return value;
    }

    /**
     * Retrieves the results exception.
     *
     * @return The result exception.
     */
    public Exception getException() { return exception; }

    /**
     * Retrieves the status of the action run.
     *
     * @return The status of the action run.
     */
    public Status getStatus() {
        return status;
    }
}

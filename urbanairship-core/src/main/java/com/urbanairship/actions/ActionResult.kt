/* Copyright Airship and Contributors */
package com.urbanairship.actions

import androidx.annotation.IntDef
import com.urbanairship.json.JsonValue

/**
 * Stores the results of running an [com.urbanairship.actions.Action].
 */
public sealed class ActionResult private constructor(
    public val value: ActionValue = ActionValue(),
    public val status: Status
) {
    public enum class Status {
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
         * in the [com.urbanairship.actions.ActionRegistry]. This value is
         * only possible if trying to run an action by name through the
         * [com.urbanairship.actions.ActionRunRequestFactory].
         */
        ACTION_NOT_FOUND,

        /**
         * The action encountered a runtime exception during execution. The
         * exception field will contain the caught exception.
         */
        EXECUTION_ERROR
    }

    public class Empty @JvmOverloads constructor(
        status: Status = Status.COMPLETED
    ) : ActionResult(status = status)

    public class Error @JvmOverloads constructor(
        public val exception: Exception,
        status: Status = Status.EXECUTION_ERROR
    ) : ActionResult(status = status)

    public class Value @JvmOverloads constructor(
        result: ActionValue,
        status: Status = Status.COMPLETED
    ) : ActionResult(result, status)

    public companion object {
        /**
         * Factory method to create an empty result
         */
        @JvmStatic
        public fun newEmptyResult(): ActionResult = Empty()

        /**
         * Factory method to create a result with a value
         *
         * @param value The result value
         */
        @JvmStatic
        public fun newResult(value: ActionValue): ActionResult = Value(value)

        /**
         * Factory method to create a result with an exception
         *
         * @param exception The result value
         */
        @JvmStatic
        public fun newErrorResult(exception: Exception): ActionResult = Error(exception)

        /**
         * Factory method to create an empty result with a specific status
         *
         * @param status The result's status
         */
        public fun newEmptyResultWithStatus(status: Status): ActionResult = Empty(status)
    }
}

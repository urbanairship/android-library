/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.os.Bundle
import com.urbanairship.actions.Action.Situation
import com.urbanairship.actions.ActionValue.Companion.wrap

/**
 * Utilities for writing action tests
 */
public object ActionTestUtils {

    /**
     * Creates an ActionArgument.
     *
     * @param situation The situation.
     * @param value The action value.
     * @return ActionArguments that contain the situation and value.
     */
    public fun createArgs(situation: Situation, value: ActionValue): ActionArguments {
        return createArgs(situation, value, null)
    }

    /**
     * Creates an ActionArgument.
     *
     * @param situation The situation.
     * @param value The action value.
     * @param metadata The metadata.
     * @return ActionArguments that contain the situation, value, and metadata.
     */
    /**
     * Creates an ActionArgument and automatically wraps the value into an ActionValue.
     *
     * @param situation The situation.
     * @param value The action value.
     * @return ActionArguments that contain the situation and value.
     */
    @JvmOverloads
    public fun createArgs(
        situation: Situation,
        value: Any,
        metadata: Bundle? = null
    ): ActionArguments {
        try {
            return createArgs(
                situation = situation,
                actionValue = wrap(value),
                metadata = metadata ?: Bundle())
        } catch (e: ActionValueException) {
            // Throw an illegal argument exception to fail the test

            throw IllegalArgumentException(
                "Object value: $value unable to be wrapped as an action value.", e
            )
        }
    }

    /**
     * Creates an ActionArgument and automatically wraps the value into an ActionValue.
     *
     * @param situation The situation.
     * @param value The action value.
     * @param metadata The metadata.
     * @return ActionArguments that contain the situation, value, and metadata.
     */
    public fun createArgs(situation: Situation, actionValue: ActionValue, metadata: Bundle): ActionArguments {
        return ActionArguments(situation, actionValue, metadata)
    }

    /**
     * Creates an ActionResult and automatically wraps the value into an ActionValue.
     *
     * @param value The ActionResult value.
     * @param exception The ActionResult exception.
     * @param status The ActionResult status.
     * @return ActionResult that contains the value, exception, and status.
     */
    @JvmStatic
    public fun createResult(
        value: Any?, exception: Exception?, status: ActionResult.Status
    ): ActionResult {
        try {
            value?.let { return ActionResult.newResult(wrap(value)) }

            exception?.let { return ActionResult.newErrorResult(exception) }

            return ActionResult.newEmptyResultWithStatus(status)
        } catch (e: ActionValueException) {
            // Throw an illegal argument exception to fail the test
            throw IllegalArgumentException(
                "Object value: $value unable to be wrapped as an action value.", e
            )
        }
    }
}

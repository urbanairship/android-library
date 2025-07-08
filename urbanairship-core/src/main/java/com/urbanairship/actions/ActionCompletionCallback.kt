/* Copyright Airship and Contributors */
package com.urbanairship.actions

/**
 * An interface for callbacks signaling the completion of an
 * [com.urbanairship.actions.Action].
 */
public interface ActionCompletionCallback {

    /**
     * Signals completion of the action.
     *
     * @param arguments The action arguments.
     * @param result The result of the action.
     */
    public fun onFinish(arguments: ActionArguments, result: ActionResult)
}

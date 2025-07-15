/* Copyright Airship and Contributors */
package com.urbanairship.actions

import com.urbanairship.UALog

/**
 * The base action class that describes an operation to perform.
 *
 *
 * An action is an abstraction over a unary function, which takes
 * [com.urbanairship.actions.ActionArguments] and performs a defined task,
 * producing an optional [com.urbanairship.actions.ActionResult]. Actions
 * may restrict or vary the work they perform depending on the arguments they
 * receive, which may include type introspection and runtime context.
 *
 *
 * In the larger view, the Actions framework provides a convenient way to
 * automatically perform tasks by name in response to push notifications,
 * Rich App Page interactions and JavaScript.
 *
 *
 * The UA library comes with pre-made actions for common tasks such as setting
 * tags and opening URLs out of the box, but this class can also be extended to
 * enable custom app behaviors and engagement experiences.
 *
 *
 * While actions can be run manually, typically they are associated with names
 * in the [com.urbanairship.actions.ActionRegistry], and run
 * on their own threads with the [com.urbanairship.actions.ActionRunRequest].
 *
 *
 * Actions that are either long lived or are unable to be interrupted by the device
 * going to sleep should request a wake lock before performing. This is especially
 * important for actions that are performing in SITUATION_PUSH_RECEIVED, when a
 * push is delivered when the device is not active.
 *
 *
 * The value returned by [.shouldRunOnMainThread] determines which thread should
 * run the action if executed asynchronously. If an action involves a UI interaction, this
 * method should be overridden to return true so that the action definitely runs before the
 * app state changes.
 */
public abstract class Action public constructor() {

    public enum class Situation {
        /**
         * Situation where an action is manually invoked.
         */
        MANUAL_INVOCATION,

        /**
         * Situation where an action is triggered when a push is received.
         */
        PUSH_RECEIVED,

        /**
         * Situation where an action is triggered when a push is opened.
         */
        PUSH_OPENED,

        /**
         * Situation where an action is triggered from a web view.
         */
        WEB_VIEW_INVOCATION,

        /**
         * Situation where an action is triggered from a foreground notification action button.
         */
        FOREGROUND_NOTIFICATION_ACTION_BUTTON,

        /**
         * Situation where an action is triggered from a background notification action button.
         */
        BACKGROUND_NOTIFICATION_ACTION_BUTTON,

        /**
         * Situation where an action is triggered from Action Automation.
         */
        AUTOMATION
    }

    /**
     * Performs the action, with pre/post execution calls,
     * if it accepts the provided arguments.
     *
     * @param arguments The action arguments.
     * @return The result of the action.
     */
    public fun run(arguments: ActionArguments): ActionResult {
        try {
            if (!acceptsArguments(arguments)) {
                UALog.d("Action $this is unable to accept arguments: $arguments}")
                return ActionResult.newEmptyResultWithStatus(ActionResult.Status.REJECTED_ARGUMENTS)
            }

            UALog.i("Running action: $this arguments: $arguments")
            onStart(arguments)

            val result = perform(arguments)

            onFinish(arguments, result)
            return result
        } catch (e: Exception) {
            UALog.e(e, "Failed to run action $this")
            return ActionResult.newErrorResult(e)
        }
    }

    /**
     * Called before an action is performed to determine if the
     * the action can accept the arguments.
     *
     * @param arguments The action arguments.
     * @return `true` if the action can perform with the arguments,
     * otherwise `false`.
     */
    public open fun acceptsArguments(arguments: ActionArguments): Boolean {
        return true
    }

    /**
     * Called before an action is performed.
     *
     * @param arguments The action arguments.
     */
    public open fun onStart(arguments: ActionArguments) { }

    /**
     * Performs the action.
     *
     * @param arguments The action arguments.
     * @return The result of the action.
     */
    public abstract fun perform(arguments: ActionArguments): ActionResult

    /**
     * Called after the action performs.
     *
     * @param arguments The action arguments.
     * @param result The result of the action.
     */
    public open fun onFinish(arguments: ActionArguments, result: ActionResult) { }

    /**
     * Determines which thread runs the action.
     *
     * @return `true` if the action should be run on the main thread, or `false` if the
     * action should run on a background thread.
     */
    public open fun shouldRunOnMainThread(): Boolean {
        return false
    }
}

/* Copyright Airship and Contributors */
package com.urbanairship.actions

import com.urbanairship.UALog
import kotlinx.coroutines.CancellationException

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
 *
 *
 * By default, an action performs its work synchronously by implementing [perform]. An action
 * whose work is naturally asynchronous (e.g. it calls a `suspend` API) should instead implement
 * [performSuspending], or extend [SuspendingAction], so the actions framework can await it
 * properly instead of the action having to block a thread with `runBlocking` or fire its work
 * off without waiting for it to finish. Every entry point into the framework
 * ([com.urbanairship.actions.ActionRunRequest], [com.urbanairship.actions.ActionRunner], the
 * JavaScript bridge, push and Scene/IAA actions) awaits [performSuspending] internally, so
 * existing callers see no behavior change either way.
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
     * This blocks the calling thread until [perform] returns. If the action is a
     * [SuspendingAction] (or otherwise overrides [performSuspending]), the underlying suspending
     * work is bridged onto this thread via `runBlocking`. Do not call this from the main
     * thread in that case. Prefer [runSuspending] from a coroutine.
     *
     * @param arguments The action arguments.
     * @return The result of the action.
     */
    public fun run(arguments: ActionArguments): ActionResult {
        try {
            rejectedResultOrNull(arguments)?.let { return it }

            val result = perform(arguments)

            onFinish(arguments, result)
            return result
        } catch (e: Exception) {
            UALog.e(e, "Failed to run action $this")
            return ActionResult.newErrorResult(e)
        }
    }

    /**
     * Performs the action, with pre/post execution calls, if it accepts the provided
     * arguments, suspending until the action completes.
     *
     * This is the suspending counterpart to [run]: it does the same pre/post-execution work
     * (rejecting unaccepted arguments, calling [onStart] and [onFinish]) but awaits
     * [performSuspending] instead of blocking a thread. Actions that only implement the
     * blocking [perform] behave exactly as they do through [run].
     *
     * A [kotlinx.coroutines.CancellationException] from a canceled caller is rethrown as-is,
     * rather than being reported as an [ActionResult.Status.EXECUTION_ERROR] result. [onFinish]
     * is not called in that case.
     *
     * @param arguments The action arguments.
     * @return The result of the action.
     */
    public suspend fun runSuspending(arguments: ActionArguments): ActionResult {
        try {
            rejectedResultOrNull(arguments)?.let { return it }

            val result = performSuspending(arguments)

            onFinish(arguments, result)
            return result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UALog.e(e, "Failed to run action $this")
            return ActionResult.newErrorResult(e)
        }
    }

    /**
     * Shared pre-execution step for [run] and [runSuspending]: rejects unaccepted arguments,
     * otherwise logs and calls [onStart].
     *
     * @return A rejected [ActionResult] if [arguments] weren't accepted, otherwise `null` to
     * signal the caller should proceed to [perform]/[performSuspending].
     */
    private fun rejectedResultOrNull(arguments: ActionArguments): ActionResult? {
        if (!acceptsArguments(arguments)) {
            UALog.d("Action $this is unable to accept arguments: $arguments}")
            return ActionResult.newEmptyResultWithStatus(ActionResult.Status.REJECTED_ARGUMENTS)
        }

        UALog.i("Running action: $this arguments: $arguments")
        onStart(arguments)
        return null
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
     * Implement this for an action whose work is synchronous. An action whose work is
     * naturally asynchronous should implement [performSuspending] instead (see [SuspendingAction]);
     * this method is still required for source/binary compatibility, but a [SuspendingAction]
     * provides it for you.
     *
     * @param arguments The action arguments.
     * @return The result of the action.
     */
    public abstract fun perform(arguments: ActionArguments): ActionResult

    /**
     * Performs the action, suspending until it completes.
     *
     * The default implementation delegates to the blocking [perform], so existing actions need
     * no changes. Override this method (or extend [SuspendingAction]) to define an action that
     * suspends instead of blocking, like one that calls a `suspend` API directly instead of
     * bridging it with `runBlocking`.
     *
     * @param arguments The action arguments.
     * @return The result of the action.
     */
    public open suspend fun performSuspending(arguments: ActionArguments): ActionResult {
        return perform(arguments)
    }

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

/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.urbanairship.AirshipExecutors
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.actions.Action.Situation
import java.util.concurrent.Executor
import java.util.concurrent.Semaphore
import kotlin.concurrent.Volatile

/**
 * ActionRunRequests provides a fluent API for running Actions.
 *
 *
 * If an action entails a UI interaction, [Action.shouldRunOnMainThread] will be
 * overridden to return true so that the action runs on the UI thread when triggered
 * asynchronously. If called by the UI thread, the action will run immediately, otherwise it will
 * be posted to the main thread's looper. All other actions will run on their own thread.
 * If the async run is triggered on the UI thread or a thread with a prepared looper,
 * the optional [com.urbanairship.actions.ActionCompletionCallback] will be
 * executed on the calling thread by sending a message to the calling thread's handler.
 * If the calling thread does not have a prepared looper, the callback will be
 * executed on the main thread.
 *
 *
 * Synchronous runs will block, and should never be called on the UI thread.
 * It should only be used when executing actions in a separate thread, or as a
 * convenient way of running actions from another action.
 */
public open class ActionRunRequest {

    private var registry: ActionRegistry? = null
    private var actionName: String? = null
    private var action: Action? = null
    private var actionValue: ActionValue? = null
    private var metadata: Bundle? = null
    private var executor: Executor = AirshipExecutors.threadPoolExecutor()
    private var situation = Situation.MANUAL_INVOCATION

    /**
     * Creates a new action RunRequest.
     *
     * @param actionName The action name in the registry.
     * @param registry Optional - The action registry to look up the action. Defaults to [com.urbanairship.UAirship.getActionRegistry]
     */
    private constructor(actionName: String, registry: ActionRegistry?) {
        this.actionName = actionName
        this.registry = registry
    }

    /**
     * Creates a new action RunRequest.
     *
     * @param action The action to run.
     * @hide
     */
    @VisibleForTesting
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public constructor(action: Action) {
        this.action = action
    }

    /**
     * Sets the action argument's value.
     *
     * @param actionValue The action argument's value.
     * @return The request object.
     */
    public fun setValue(actionValue: ActionValue?): ActionRunRequest {
        return apply { this.actionValue = actionValue }
    }

    /**
     * Sets the action arguments value. The object will automatically be wrapped
     * as a ActionValue and throw an illegal argument exception if its an invalid value.
     *
     * @param object The action arguments as an object.
     * @return The request object.
     * @throws IllegalArgumentException if the object is unable to be wrapped in an ActionValue.
     */
    public open fun setValue(`object`: Any?): ActionRunRequest {
        try {
            this.actionValue = ActionValue.wrap(`object`)
        } catch (e: ActionValueException) {
            throw IllegalArgumentException("Unable to wrap object: $`object` as an ActionValue.", e)
        }
        return this
    }

    /**
     * Sets the action argument's metadata.
     *
     * @param metadata The action argument's metadata.
     * @return The request object.
     */
    public open fun setMetadata(metadata: Bundle?): ActionRunRequest {
        return apply { this.metadata = metadata }
    }

    /**
     * Sets the situation.
     *
     * @param situation The action argument's situation.
     * @return The request object.
     */
    public open fun setSituation(situation: Situation): ActionRunRequest {
        return apply { this.situation = situation }
    }

    /**
     * Sets the executor.
     *
     * @param executor The executor.
     * @return The request object.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun setExecutor(executor: Executor): ActionRunRequest {
        return apply { this.executor = executor }
    }

    /**
     * Executes the action synchronously.
     *
     * @return The action's result.
     */
    @WorkerThread
    public open fun runSync(): ActionResult {
        val arguments = createActionArguments()
        val semaphore = Semaphore(0)

        val runnable = object : ActionRunnable(arguments) {
            override fun onFinish(arguments: ActionArguments, result: ActionResult) {
                semaphore.release()
            }
        }

        if (shouldRunOnMain(arguments)) {
            Handler(Looper.getMainLooper()).post(runnable)
        } else {
            executor.execute(runnable)
        }

        try {
            semaphore.acquire()
        } catch (ex: InterruptedException) {
            UALog.e("Failed to run action with arguments $arguments")
            Thread.currentThread().interrupt()
            return ActionResult.newErrorResult(ex)
        }

        return runnable.result ?: ActionResult.newEmptyResult()
    }

    /**
     * Executes the action asynchronously.
     */
    public open fun run() {
        run(null, null)
    }

    /**
     * Executes the action asynchronously with a callback.
     *
     * @param callback The action completion callback.
     */
    public open fun run(callback: ActionCompletionCallback?) {
        run(null, callback)
    }

    /**
     * Executes the action asynchronously with a callback.
     *
     * @param callback The action completion callback.
     * @param looper A Looper object whose message queue will be used for the callback,
     * or null to make callbacks on the calling thread or main thread if the current thread
     * does not have a looper associated with it.
     */
    public open fun run(looper: Looper?, callback: ActionCompletionCallback?) {
        val runLooper = looper ?: Looper.myLooper() ?: Looper.getMainLooper()

        val arguments = createActionArguments()
        val handler = Handler(runLooper)

        val runnable = object : ActionRunnable(arguments) {
            override fun onFinish(arguments: ActionArguments, result: ActionResult) {
                if (callback == null) {
                    return
                }

                if (handler.looper == Looper.myLooper()) {
                    callback.onFinish(arguments, result)
                } else {
                    handler.post { callback.onFinish(arguments, result) }
                }
            }
        }

        if (shouldRunOnMain(arguments)) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                runnable.run()
            } else {
                Handler(Looper.getMainLooper()).post(runnable)
            }
        } else {
            executor.execute(runnable)
        }
    }

    /**
     * Helper method to construct the action arguments.
     *
     * @return The action arguments.
     */
    private fun createActionArguments(): ActionArguments {
        val metadata = this.metadata ?: Bundle()
        actionName?.let {
            metadata.putString(ActionArguments.REGISTRY_ACTION_NAME_METADATA, it)
        }

        return ActionArguments(
            situation = situation,
            value = actionValue ?: ActionValue(),
            metadata = metadata
        )
    }

    /**
     * Helper method to look an action registry entry.
     *
     * @param actionName The action name in the registry.
     * @return The action registry entry if found, or null.
     */
    private fun lookUpAction(actionName: String): ActionRegistry.Entry? {
        return registry?.getEntry(actionName)
            ?: UAirship.shared().actionRegistry.getEntry(actionName)
    }

    /**
     * Helper method to check if the request should run on the main thread.
     *
     * @param arguments The action arguments.
     * @return `true` if the action should run on the main thread, otherwise `false`
     */
    private fun shouldRunOnMain(arguments: ActionArguments): Boolean {

        action?.let { return it.shouldRunOnMainThread() }

        return this.actionName?.let { lookUpAction(it) }
            ?.getActionForSituation(arguments.situation)
            ?.shouldRunOnMainThread()
            ?: false
    }

    /**
     * Helper method to actually run the action.
     *
     * @param arguments The action arguments.
     * @return The action's result.
     */
    private fun executeAction(arguments: ActionArguments): ActionResult {

        actionName?.let { name ->
            val entry = lookUpAction(name)
            if (entry == null) {
                return ActionResult.newEmptyResultWithStatus(ActionResult.Status.ACTION_NOT_FOUND)
            } else if (entry.predicate?.apply(arguments) == false) {
                UALog.i( "Action $name will not be run. Registry predicate rejected the arguments: $arguments")
                return ActionResult.newEmptyResultWithStatus(ActionResult.Status.REJECTED_ARGUMENTS)
            } else {
                return entry.getActionForSituation(situation).run(arguments)
            }
        }

        action?.let { return it.run(arguments) }

        return ActionResult.newEmptyResultWithStatus(ActionResult.Status.ACTION_NOT_FOUND)
    }

    /**
     * Helper runnable for running the action request and retaining the result.
     */
    private abstract inner class ActionRunnable(private val arguments: ActionArguments) : Runnable {

        @Volatile
        var result: ActionResult? = null

        override fun run() {
            val executionResult = executeAction(arguments)
            result = executionResult
            onFinish(arguments, executionResult)
        }

        /**
         * Called when the action is finished.
         *
         * @param arguments The arguments.
         * @param result The action result.
         */
        abstract fun onFinish(arguments: ActionArguments, result: ActionResult)
    }

    public companion object {

        /**
         * Creates an action run request. The action will not be run
         * until running the request.
         *
         * @param actionName The action name in the registry.
         * @param registry Optional - The action registry to look up the action. If null, the registry
         * from [com.urbanairship.UAirship.getActionRegistry] will be used.
         * @return An action run request.
         */
        @JvmStatic
        @JvmOverloads
        public fun createRequest(actionName: String, registry: ActionRegistry? = null): ActionRunRequest {
            return ActionRunRequest(actionName, registry)
        }

        /**
         * Creates an action run request. The action will not be run
         * until running the request.
         *
         * @param action The action to run.
         * @return An action run request.
         * @throws java.lang.IllegalArgumentException if the action is null.
         */
        @JvmStatic
        public fun createRequest(action: Action): ActionRunRequest = ActionRunRequest(action)
    }
}

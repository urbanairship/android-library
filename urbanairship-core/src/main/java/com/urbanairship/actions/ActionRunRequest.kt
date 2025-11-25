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
import com.urbanairship.Airship
import com.urbanairship.AirshipDispatchers
import com.urbanairship.actions.Action.Situation
import com.urbanairship.contacts.Scope
import java.util.concurrent.Executor
import java.util.concurrent.Semaphore
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.Volatile
import kotlin.concurrent.withLock
import com.google.android.gms.common.internal.Objects
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
    private var metadataLock: ReentrantLock = ReentrantLock()
    private var scope: CoroutineScope
    private var situation = Situation.MANUAL_INVOCATION

    /**
     * Creates a new action RunRequest.
     *
     * @param actionName The action name in the registry.
     * @param registry Optional - The action registry to look up the action. Defaults to [com.urbanairship.Airship.getActionRegistry]
     */
    private constructor(
        actionName: String,
        registry: ActionRegistry?,
        dispatcher: CoroutineDispatcher = AirshipDispatchers.IO
    ) {
        this.actionName = actionName
        this.registry = registry
        this.scope = CoroutineScope(dispatcher)
    }

    /**
     * Creates a new action RunRequest.
     *
     * @param action The action to run.
     * @hide
     */
    @VisibleForTesting
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public constructor(action: Action, dispatcher: CoroutineDispatcher = AirshipDispatchers.IO) {
        this.action = action
        this.scope = CoroutineScope(dispatcher)
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
        return apply {
            metadataLock.withLock {
                if (metadata == null) {
                    this.metadata = null
                } else {
                    this.metadata = Bundle(metadata)
                }
            }
        }
    }

    /**
     * Sets the coroutine dispatcher.
     *
     * @param dispatcher The coroutine dispatcher.
     * @return The request object.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun setDispatcher(dispatcher: CoroutineDispatcher): ActionRunRequest {
        return apply { this.scope = CoroutineScope(dispatcher) }
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
     * Executes the action synchronously.
     *
     * @return The action's result.
     */
    @WorkerThread
    public open fun runSync(): ActionResult {
        return try {
            runBlocking { runSuspending().result }
        } catch (ex: InterruptedException) {
            UALog.e("Failed to run action with arguments ${createActionArguments()}")
            ActionResult.newErrorResult(ex)
        }
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
     * Executes the action.
     */
    public open suspend fun runSuspending(): ActionRunResult {
        val arguments = createActionArguments()
        val executionScope = getExecutionScope(arguments)

        val result = executeAction(executionScope, arguments)
        return ActionRunResult(
            arguments = arguments,
            result = result
        )
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
        val handler = Handler(runLooper)

        scope.launch {
            val result = runSuspending()

            handler.post { callback?.onFinish(result.arguments, result.result) }
        }
    }

    /**
     * Helper method to construct the action arguments.
     *
     * @return The action arguments.
     */
    private fun createActionArguments(): ActionArguments {
        val metadata = metadataLock.withLock {
            val metadata = this.metadata ?: Bundle()
            actionName?.let {
                metadata.putString(ActionArguments.REGISTRY_ACTION_NAME_METADATA, it)
            }
            metadata
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

        val entry = registry?.getEntry(actionName)
        if (entry != null) {
            return entry
        }
        return if (Airship.isFlyingOrTakingOff) {
            Airship.actionRegistry.getEntry(actionName)
        } else {
            null
        }
    }

    private fun getExecutionScope(arguments: ActionArguments): CoroutineScope {
        return if (shouldRunOnMain(arguments)) {
            CoroutineScope(Dispatchers.Main)
        } else {
            scope
        }
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
    private suspend fun executeAction(scope: CoroutineScope, arguments: ActionArguments): ActionResult {
        return scope.async {
            actionName?.let { name ->
                val entry = lookUpAction(name)
                if (entry == null) {
                    return@async ActionResult.newEmptyResultWithStatus(ActionResult.Status.ACTION_NOT_FOUND)
                } else if (entry.predicate?.apply(arguments) == false) {
                    UALog.i( "Action $name will not be run. Registry predicate rejected the arguments: $arguments")
                    return@async  ActionResult.newEmptyResultWithStatus(ActionResult.Status.REJECTED_ARGUMENTS)
                } else {
                    return@async  entry.getActionForSituation(situation).run(arguments)
                }
            }

            action?.let { return@async  it.run(arguments) }

            ActionResult.newEmptyResultWithStatus(ActionResult.Status.ACTION_NOT_FOUND)
        }.await()
    }

    public class ActionRunResult(
        public val arguments: ActionArguments,
        public val result: ActionResult
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ActionRunResult

            if (arguments != other.arguments) return false
            if (result != other.result) return false

            return true
        }

        override fun hashCode(): Int {
            return Objects.hashCode(arguments, result)
        }
    }

    public companion object {

        /**
         * Creates an action run request. The action will not be run
         * until running the request.
         *
         * @param actionName The action name in the registry.
         * @param registry Optional - The action registry to look up the action. If null, the registry
         * from [com.urbanairship.Airship.actionRegistry] will be used.
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

/* Copyright Airship and Contributors */
package com.urbanairship.actions

import androidx.annotation.WorkerThread
import kotlinx.coroutines.runBlocking

/**
 * An [Action] that suspends while performing its work instead of blocking its calling thread.
 *
 * Subclasses implement [performSuspending] instead of [Action.perform]. When the action is run
 * through the actions framework ([ActionRunRequest], [ActionRunner], the JavaScript bridge,
 * push actions, Scenes/IAA button actions, etc.) the framework awaits [performSuspending], so
 * the [ActionResult] reflects the completed work.
 *
 * Blocking entry points ([Action.run], [ActionRunRequest.runSync]) are still supported for
 * source and binary compatibility, but they block the calling thread until the action
 * completes and must not be called from the main thread.
 */
public abstract class SuspendingAction public constructor() : Action() {

    /**
     * Performs the action, suspending until it completes.
     *
     * @param arguments The action arguments.
     * @return The result of the action.
     */
    public abstract override suspend fun performSuspending(arguments: ActionArguments): ActionResult

    /**
     * Blocking bridge to [performSuspending], provided for legacy callers.
     *
     * Do not call from the main thread.
     */
    @WorkerThread
    final override fun perform(arguments: ActionArguments): ActionResult {
        return runBlocking { performSuspending(arguments) }
    }
}

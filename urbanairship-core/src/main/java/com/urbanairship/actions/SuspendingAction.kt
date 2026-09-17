/* Copyright Airship and Contributors */
package com.urbanairship.actions

import androidx.annotation.WorkerThread
import kotlinx.coroutines.runBlocking

/**
 * An [Action] that suspends while performing its work instead of blocking its calling thread.
 *
 * Subclasses implement [performSuspending] instead of [Action.perform]. The actions framework
 * ([ActionRunRequest], [ActionRunner], the JavaScript bridge, push and Scene/IAA actions) awaits
 * it internally, so the [ActionResult] reflects the completed work.
 *
 * Blocking entry points ([Action.run], [ActionRunRequest.runSync]) still work for compatibility,
 * but block the calling thread until the action completes; don't call them from the main thread.
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

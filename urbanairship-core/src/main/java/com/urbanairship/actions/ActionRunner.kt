/* Copyright Airship and Contributors */

package com.urbanairship.actions

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonSerializable
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Runs actions.
 */
public interface ActionRunner {

    /**
     * Called to run an action.
     * @param name The action name.
     * @param value The action value.
     * @param situation The situation. Defaults to [Action.Situation.MANUAL_INVOCATION]
     * @param extender An optional request extender.
     * @param callback An optional completion callback.
     */
    public fun run(
        name: String,
        value: JsonSerializable?,
        situation: Action.Situation? = null,
        extender: ActionRunRequestExtender? = null,
        callback: ActionCompletionCallback? = null
    )
}

/**
 * Default action runner.
 */
public object DefaultActionRunner: ActionRunner {

    override fun run(
        name: String,
        value: JsonSerializable?,
        situation: Action.Situation?,
        extender: ActionRunRequestExtender?,
        callback: ActionCompletionCallback?
    ) {
        ActionRunRequest.createRequest(name)
            .setValue(value)
            .let { request ->
                request.setSituation(situation ?: Action.Situation.MANUAL_INVOCATION)

                extender?.let {
                    extender.extend(request)
                } ?: request
            }
            .run(callback)
    }
}

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public suspend fun ActionRunner.runSuspending(
    name: String,
    value: JsonSerializable? = null,
    situation: Action.Situation? = null,
    extender: ActionRunRequestExtender? = null
): ActionResult {
    return suspendCancellableCoroutine { continuation ->
        this.run(
            name = name,
            value = value,
            situation = situation,
            extender = extender,
            callback = object : ActionCompletionCallback {
                override fun onFinish(arguments: ActionArguments, result: ActionResult) {
                    continuation.resume(result)
                }
            }
        )
    }
}

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public suspend fun ActionRunner.runSuspending(
    actions: Map<String, JsonSerializable>,
    situation: Action.Situation? = null,
    extender: ActionRunRequestExtender? = null
) {
    actions.forEach {
        runSuspending(it.key, it.value, situation, extender)
    }
}

public fun ActionRunner.run(
    actions: Map<String, JsonSerializable>,
    situation: Action.Situation? = null,
    extender: ActionRunRequestExtender? = null
) {
    actions.forEach {
        this.run(it.key, it.value, situation, extender)
    }
}

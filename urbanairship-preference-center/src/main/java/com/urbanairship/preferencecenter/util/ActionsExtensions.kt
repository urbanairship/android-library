package com.urbanairship.preferencecenter.util

import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionResult
import com.urbanairship.actions.ActionRunRequestFactory
import com.urbanairship.json.JsonValue

/** A map of actions to perform. */
internal typealias ActionsMap = Map<String, JsonValue>

/**
 * Executes all actions contained in this map.
 *
 * @param requestFactory Optional action request factory.
 * @param onComplete Optional action completion callback.
 */
internal fun ActionsMap.execute(
    requestFactory: ActionRunRequestFactory = ActionRunRequestFactory(),
    onComplete: (arguments: ActionArguments, result: ActionResult) -> Unit = { _, _ -> }
) =
    forEach { (name, value) ->
        requestFactory.createActionRequest(name)
            .setValue(value)
            .run(onComplete)
    }

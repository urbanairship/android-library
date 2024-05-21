/* Copyright Airship and Contributors */
package com.urbanairship.iam

import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionRunRequest
import com.urbanairship.actions.ActionRunRequestFactory
import com.urbanairship.iam.info.InAppMessageButtonInfo
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

/**
 * Action utils for in-app messaging.
 */
internal object InAppActionUtils {

    /**
     * Runs actions from the button info.
     *
     * @param buttonInfo The button info.
     */
    fun runActions(buttonInfo: InAppMessageButtonInfo?) {
        runActions(buttonInfo?.actions)
    }

    /**
     * Runs a map of actions.
     *
     * @param actionsMap The action map.
     * @param requestFactory Optional action request factory.
     * @param runSync If the actions should be run synchronously.
     */
    @JvmOverloads
    fun runActions(
        actionsMap: JsonMap?,
        requestFactory: ActionRunRequestFactory? = null,
        @Action.Situation situation: Int? = null,
        runSync: Boolean = false
    ) {
        val actions = actionsMap ?: return

        actions.map {
            val result  = requestFactory?.createActionRequest(it.key)
                ?: ActionRunRequest.createRequest(it.key)
            result.apply {
                setValue(it.value)
                situation?.let(this::setSituation)
            }
        }.forEach {
            if (runSync) {
                it.runSync()
            } else {
                it.run()
            }
        }
    }
}

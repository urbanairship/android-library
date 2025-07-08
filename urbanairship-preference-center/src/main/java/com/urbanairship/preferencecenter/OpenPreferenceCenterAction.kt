package com.urbanairship.preferencecenter

import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionResult
import com.urbanairship.util.AirshipComponentUtils
import java.util.concurrent.Callable

/**
 * Opens a Preference Center.
 */
public class OpenPreferenceCenterAction(
    private val preferenceCenterCallable: Callable<PreferenceCenter> = AirshipComponentUtils.callableForComponent(PreferenceCenter::class.java)
) : Action() {

    override fun acceptsArguments(arguments: ActionArguments): Boolean =
        when (arguments.situation) {
            Situation.PUSH_OPENED,
            Situation.WEB_VIEW_INVOCATION,
            Situation.MANUAL_INVOCATION,
            Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON,
            Situation.AUTOMATION -> true
            else -> false
        }

    override fun perform(arguments: ActionArguments): ActionResult {
        val preferenceCenterId = arguments.value.map?.opt("preference_center_id")?.string
        return if (preferenceCenterId == null) {
            val msg = "Failed to perform OpenPreferenceCenterAction! Required argument 'preference_center_id' is null."
            ActionResult.newErrorResult(IllegalArgumentException(msg))
        } else {
            preferenceCenterCallable.call().open(preferenceCenterId)
            ActionResult.newEmptyResult()
        }
    }

    override fun shouldRunOnMainThread(): Boolean = true
}

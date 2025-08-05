/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.widget.Toast
import com.urbanairship.UAirship
import com.urbanairship.actions.ActionResult.Companion.newResult

/**
 * An action that displays text in a toast.
 *
 *
 * Accepted situations: [Action.Situation.PUSH_OPENED], [Action.Situation.WEB_VIEW_INVOCATION],
 * [Action.Situation.MANUAL_INVOCATION], [Action.Situation.AUTOMATION],
 * [Action.Situation.BACKGROUND_NOTIFICATION_ACTION_BUTTON],
 * and [Action.Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON].
 *
 *
 * Accepted argument value - A string with the toast text or a map with:
 *
 *  * [LENGTH_KEY]: int either [Toast.LENGTH_LONG] or [Toast.LENGTH_SHORT], Optional
 *  * [TEXT_KEY]: String, Required
 *
 *
 *
 * Result value: The arguments value.
 *
 *
 * Default Registration Names: [DEFAULT_REGISTRY_NAME]
 */
public class ToastAction public constructor() : Action() {

    override fun acceptsArguments(arguments: ActionArguments): Boolean {
        return when (arguments.situation) {
            Situation.BACKGROUND_NOTIFICATION_ACTION_BUTTON,
            Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON,
            Situation.PUSH_OPENED,
            Situation.MANUAL_INVOCATION,
            Situation.WEB_VIEW_INVOCATION,
            Situation.AUTOMATION -> {
                val args = arguments.value.map ?: return arguments.value.string != null

                return args.opt(TEXT_KEY).isString
            }
            else -> false
        }
    }

    override fun perform(arguments: ActionArguments): ActionResult {
        val length = arguments.value.map?.opt(LENGTH_KEY)?.getInt(Toast.LENGTH_SHORT)
            ?: Toast.LENGTH_SHORT
        val text = arguments.value.map?.opt(TEXT_KEY)?.string
            ?: arguments.value.string

        if (length == Toast.LENGTH_LONG) {
            Toast.makeText(UAirship.applicationContext, text, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(UAirship.applicationContext, text, Toast.LENGTH_SHORT).show()
        }

        return newResult(arguments.value)
    }

    override fun shouldRunOnMainThread(): Boolean {
        return true
    }

    public companion object {

        /**
         * Default registry name
         */
        public const val DEFAULT_REGISTRY_NAME: String = "toast_action"

        /**
         * Key to define the Toast's text when providing the action's value as a map.
         */
        public const val TEXT_KEY: String = "text"

        /**
         * Key to define the Toast's length when providing the action's value as a map.
         */
        public const val LENGTH_KEY: String = "length"
    }
}

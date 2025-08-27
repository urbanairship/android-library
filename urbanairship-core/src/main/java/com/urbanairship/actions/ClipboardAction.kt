/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.urbanairship.Airship
import com.urbanairship.actions.ActionResult.Companion.newResult
import com.urbanairship.json.optionalField

/**
 * An action that adds text to the clipboard.
 *
 *
 * Accepted situations: [Action.Situation.PUSH_OPENED], [Action.Situation.WEB_VIEW_INVOCATION],
 * [Action.Situation.MANUAL_INVOCATION], [Action.Situation.BACKGROUND_NOTIFICATION_ACTION_BUTTON],
 * [Action.Situation.AUTOMATION], and [Action.Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON].
 *
 *
 * Accepted argument value - A string with the clipboard text or a map with:
 *
 *  * [.LABEL_KEY]: String, Optional
 *  * [.TEXT_KEY]: String, Required
 *
 *
 *
 * Result value: The arguments value.
 *
 *
 * Default Registration Names: [DEFAULT_REGISTRY_SHORT_NAME], [DEFAULT_REGISTRY_NAME]
 */
public class ClipboardAction public constructor() : Action() {

    override fun acceptsArguments(arguments: ActionArguments): Boolean {
        when (arguments.situation) {
            Situation.BACKGROUND_NOTIFICATION_ACTION_BUTTON,
            Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON,
            Situation.PUSH_OPENED,
            Situation.MANUAL_INVOCATION,
            Situation.WEB_VIEW_INVOCATION,
            Situation.AUTOMATION -> {
                val map = arguments.value.map ?: return arguments.value.string != null

                return map.opt(TEXT_KEY).isString
            }
            else -> return false
        }
    }

    override fun perform(arguments: ActionArguments): ActionResult {
        // Get the text and label

        val text: String?
        val label: String?
        if (arguments.value.map != null) {
            text = arguments.value.map.optionalField(TEXT_KEY)
            label = arguments.value.map.optionalField(LABEL_KEY)
        } else {
            text = arguments.value.string
            label = null
        }

        val clipboardManager = Airship.applicationContext
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboardManager.setPrimaryClip(clip)

        // Return the text we are setting
        return newResult(arguments.value)
    }

    override fun shouldRunOnMainThread(): Boolean {
        return false
    }

    public companion object {

        /**
         * Default registry name
         */
        public const val DEFAULT_REGISTRY_NAME: String = "clipboard_action"

        /**
         * Default registry short name
         */
        public const val DEFAULT_REGISTRY_SHORT_NAME: String = "^c"

        /**
         * Key to define the ClipData's label when providing the action's value as a map.
         */
        public const val LABEL_KEY: String = "label"

        /**
         * Key to define the ClipData's text when providing the action's value as a map.
         */
        public const val TEXT_KEY: String = "text"
    }
}

/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.content.Context
import android.content.Intent
import com.urbanairship.R
import com.urbanairship.Airship
import com.urbanairship.actions.ActionResult.Companion.newEmptyResult

/**
 * Shows a chooser activity to share text.
 *
 *
 * Accepted situations: [Action.Situation.PUSH_OPENED], [Action.Situation.WEB_VIEW_INVOCATION],
 * [Action.Situation.MANUAL_INVOCATION], [Action.Situation.AUTOMATION],
 * and [Action.Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON].
 *
 *
 * Accepted argument values: A String used as the share text.
 *
 *
 * Result value: `null`
 *
 *
 * Default Registration Names: [DEFAULT_NAMES]
 */
public class ShareAction(
    private val contextProvider: () -> Context = { Airship.application }
): Action() {

    override fun acceptsArguments(arguments: ActionArguments): Boolean {
        return when (arguments.situation) {
            Situation.PUSH_OPENED,
            Situation.WEB_VIEW_INVOCATION,
            Situation.MANUAL_INVOCATION,
            Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON,
            Situation.AUTOMATION -> {
                arguments.value.string != null
            }
            else -> false
        }
    }

    override fun perform(arguments: ActionArguments): ActionResult {
        val context = contextProvider()

        val sharingIntent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, arguments.value.string)

        val chooserIntent = Intent
            .createChooser(sharingIntent, context.getString(R.string.ua_share_dialog_title))
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(chooserIntent)

        return newEmptyResult()
    }

    override fun shouldRunOnMainThread(): Boolean {
        return true
    }

    public companion object {

        /**
         * Default action names.
         */
        public val DEFAULT_NAMES: Set<String> = setOf("share_action", "^s")
    }
}

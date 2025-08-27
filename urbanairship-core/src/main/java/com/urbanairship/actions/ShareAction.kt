/* Copyright Airship and Contributors */
package com.urbanairship.actions

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
 * Default Registration Names: [DEFAULT_REGISTRY_SHORT_NAME], [DEFAULT_REGISTRY_NAME]
 */
public class ShareAction public constructor() : Action() {

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
        val context = Airship.applicationContext

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
         * Default registry name
         */
        public const val DEFAULT_REGISTRY_NAME: String = "share_action"

        /**
         * Default registry short name
         */
        public const val DEFAULT_REGISTRY_SHORT_NAME: String = "^s"
    }
}

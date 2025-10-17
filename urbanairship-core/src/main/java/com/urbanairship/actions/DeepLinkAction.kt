/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.urbanairship.Airship
import com.urbanairship.UALog
import com.urbanairship.actions.ActionResult.Companion.newResult
import com.urbanairship.actions.DeepLinkAction.Companion.DEFAULT_NAMES
import com.urbanairship.push.PushManager
import com.urbanairship.push.PushMessage

/**
 * Action for opening a deep link.
 *
 *
 * Accepted situations: [Action.Situation.PUSH_OPENED], [Action.Situation.WEB_VIEW_INVOCATION],
 * [Action.Situation.MANUAL_INVOCATION], [Action.Situation.AUTOMATION], and [Action.Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON].
 *
 *
 * Accepted argument value types: URL as string
 *
 *
 * Result value: The URI that was opened.
 *
 *
 * Default Registration Name: [DEFAULT_NAMES]
 *
 *
 * Default Registration Predicate: none
 */
public class DeepLinkAction(
    private val onDeepLink: (String) -> Boolean = {
        Airship.deepLink(deepLink = it)
    },
    private val contextProvider: () -> Context = {
        Airship.application
    }
): Action() {

    override fun perform(arguments: ActionArguments): ActionResult {
        val deepLink = arguments.value.string ?: throw IllegalArgumentException("Missing deep link.")

        UALog.i("Deep linking: $deepLink")
        if (onDeepLink(deepLink)) {
            return newResult(arguments.value)
        }

        val context = contextProvider()

        // Fallback to intent launching
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .setPackage(context.packageName)

        arguments.metadata.getParcelable<PushMessage>(ActionArguments.PUSH_MESSAGE_METADATA)?.let {
            intent.putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, it.getPushBundle())
        }

        context.startActivity(intent)

        return newResult(arguments.value)
    }

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

    override fun shouldRunOnMainThread(): Boolean {
        return true
    }

    public companion object {

        /**
         * Default action names.
         */
        public val DEFAULT_NAMES: Set<String> = setOf("deep_link_action", "^d")
    }
}

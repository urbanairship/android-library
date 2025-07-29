/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.content.Intent
import android.net.Uri
import androidx.annotation.VisibleForTesting
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.actions.ActionResult.Companion.newResult
import com.urbanairship.base.Supplier
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
 * Default Registration Names: [DEFAULT_REGISTRY_SHORT_NAME], [DEFAULT_REGISTRY_NAME]
 *
 *
 * Default Registration Predicate: none
 */
public class DeepLinkAction @VisibleForTesting internal constructor(
    private val airshipSupplier: Supplier<UAirship>
): Action() {

    public constructor() : this(Supplier<UAirship> { UAirship.shared() })

    override fun perform(arguments: ActionArguments): ActionResult {
        val deepLink = arguments.value.string ?: throw IllegalArgumentException("Missing deep link.")
        val airship = airshipSupplier.get() ?: throw IllegalArgumentException("Missing airship.")

        UALog.i("Deep linking: $deepLink")
        if (airship.deepLink(deepLink)) {
            return newResult(arguments.value)
        }

        // Fallback to intent launching
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .setPackage(UAirship.getPackageName())

        arguments.metadata.getParcelable<PushMessage>(ActionArguments.PUSH_MESSAGE_METADATA)?.let {
            intent.putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, it.getPushBundle())
        }

        UAirship.getApplicationContext().startActivity(intent)

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
         * Default registry name
         */
        public const val DEFAULT_REGISTRY_NAME: String = "deep_link_action"

        /**
         * Default registry short name
         */
        public const val DEFAULT_REGISTRY_SHORT_NAME: String = "^d"
    }
}

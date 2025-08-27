/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.core.util.Supplier
import com.urbanairship.UALog
import com.urbanairship.Airship
import com.urbanairship.UrlAllowList
import com.urbanairship.actions.ActionResult.Companion.newResult
import com.urbanairship.util.UriUtils

/**
 * Action for opening a URL for viewing.
 *
 *
 * Accepted situations: [Action.Situation.PUSH_OPENED], [Action.Situation.WEB_VIEW_INVOCATION],
 * [Action.Situation.MANUAL_INVOCATION], [Action.Situation.AUTOMATION],
 * and [Action.Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON].
 *
 *
 * Accepted argument value types: URL as a string
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
public open class OpenExternalUrlAction @VisibleForTesting internal constructor(
    private val allowListSupplier: Supplier<UrlAllowList>
) : Action() {

    /**
     * Default constructor.
     */
    public constructor() : this(Supplier<UrlAllowList> { Airship.shared().urlAllowList })

    override fun perform(arguments: ActionArguments): ActionResult {
        val uri = UriUtils.parse(arguments.value.string) ?: throw IllegalArgumentException("Invalid URL")

        UALog.i("Opening URI: $uri")

        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        Airship.applicationContext.startActivity(intent)
        return newResult(arguments.value)
    }

    /**
     * The open external URL action accepts Strings that can be parsed as URL argument value types.
     *
     * @param arguments The action arguments.
     * @return `true` if the action can perform with the arguments,
     * otherwise `false`.
     */
    override fun acceptsArguments(arguments: ActionArguments): Boolean {
        when (arguments.situation) {
            Situation.PUSH_OPENED,
            Situation.WEB_VIEW_INVOCATION,
            Situation.MANUAL_INVOCATION,
            Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON,
            Situation.AUTOMATION -> {
                if (UriUtils.parse(arguments.value.string) == null) {
                    return false
                }

                return allowListSupplier.get()
                    .isAllowed(arguments.value.string, UrlAllowList.Scope.OPEN_URL)
            }

            else -> return false
        }
    }

    override fun shouldRunOnMainThread(): Boolean {
        return true
    }

    public companion object {

        /**
         * Default registry name
         */
        public const val DEFAULT_REGISTRY_NAME: String = "open_external_url_action"

        /**
         * Default registry short name
         */
        public const val DEFAULT_REGISTRY_SHORT_NAME: String = "^u"
    }
}

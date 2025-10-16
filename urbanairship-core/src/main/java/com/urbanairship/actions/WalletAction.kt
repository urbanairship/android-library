/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.urbanairship.Airship
import com.urbanairship.Platform
import com.urbanairship.UALog
import com.urbanairship.UrlAllowList
import com.urbanairship.actions.ActionResult.Companion.newEmptyResult
import com.urbanairship.actions.OpenExternalUrlAction
import com.urbanairship.actions.WalletAction.Companion.DEFAULT_REGISTRY_NAME
import com.urbanairship.actions.WalletAction.Companion.DEFAULT_REGISTRY_SHORT_NAME

/**
 * Action for opening Android Pay deep links.
 *
 *
 * Accepted situations: [Action.Situation.PUSH_OPENED], [Action.Situation.WEB_VIEW_INVOCATION],
 * [Action.Situation.MANUAL_INVOCATION], [Action.Situation.AUTOMATION],
 * and [Action.Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON].
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
public class WalletAction internal constructor(
    allowListProvider: () -> UrlAllowList,
    private val contextProvider: () -> Context ,
    private val platformProvider: () -> Platform
) : OpenExternalUrlAction(allowListProvider, contextProvider) {

    public constructor(): this(
        allowListProvider = { Airship.urlAllowList },
        contextProvider = { Airship.application },
        platformProvider = { Airship.platform }
    )

    public override fun perform(arguments: ActionArguments): ActionResult {
        UALog.i("Processing Wallet adaptive link.")

        val context = contextProvider()
        val intent = Intent(context, WalletLoadingActivity::class.java)
            .apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setData(Uri.parse(arguments.value.string))
            }

        context.startActivity(intent)

        return newEmptyResult()
    }

    public override fun acceptsArguments(arguments: ActionArguments): Boolean {
        // Only support Android platform
        if (platformProvider() != Platform.ANDROID) {
            return false
        }

        return super.acceptsArguments(arguments)
    }

    public companion object {

        /**
         * Default registry name
         */
        public const val DEFAULT_REGISTRY_NAME: String = "wallet_action"

        /**
         * Default registry short name
         */
        public const val DEFAULT_REGISTRY_SHORT_NAME: String = "^w"
    }
}

/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.content.Context
import android.content.Intent
import com.urbanairship.Airship
import com.urbanairship.actions.ActionResult.Companion.newEmptyResult
import com.urbanairship.actions.RateAppAction.Companion.BODY_KEY
import com.urbanairship.actions.RateAppAction.Companion.DEFAULT_REGISTRY_NAME
import com.urbanairship.actions.RateAppAction.Companion.DEFAULT_REGISTRY_SHORT_NAME
import com.urbanairship.actions.RateAppAction.Companion.SHOW_LINK_PROMPT_KEY
import com.urbanairship.actions.RateAppAction.Companion.TITLE_KEY
import com.urbanairship.json.optionalField
import com.urbanairship.util.AppStoreUtils

/**
 * Action to link users to the rating section of their respective app store directly or through a prompt.
 *
 *
 * Accepted situations: [Action.Situation.MANUAL_INVOCATION], [Action.Situation.WEB_VIEW_INVOCATION],
 * [Action.Situation.PUSH_OPENED], [Action.Situation.AUTOMATION],
 * and [Action.Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON].
 *
 *
 * Expected argument values:
 * [SHOW_LINK_PROMPT_KEY]: Optional Boolean. If NO action will link directly to the Amazon or Play store
 * review page, if YES action will display a rating prompt. Defaults to NO if null.
 * [TITLE_KEY]: Optional String. String to override the link prompt's title. Header defaults to "Enjoying <App Name>?" if null.
 * [BODY_KEY]: Optional String. String to override the link prompt's body.
 * Body defaults to "Tap Rate to rate it in the app store." if null.
 *
 *
 * Result value: `null`
 *
 *
 * Default Registration Names: [DEFAULT_REGISTRY_SHORT_NAME], [DEFAULT_REGISTRY_NAME]
 *
 *
 */
public class RateAppAction public constructor(
    private val appStoreIntentProvider: () -> Intent,
    private val contextProvider: () -> Context
) : Action() {

    public constructor(): this(
        appStoreIntentProvider = {
            AppStoreUtils
                .getAppStoreIntent(
                    Airship.application, Airship.platform, Airship.airshipConfigOptions
                )
        },
        contextProvider = { Airship.application }
    )

    override fun perform(arguments: ActionArguments): ActionResult {

        val shouldShowLinkPrompt = arguments.value
            .toJsonValue()
            .optMap()
            .opt(SHOW_LINK_PROMPT_KEY)
            .getBoolean(false)

        if (shouldShowLinkPrompt) {
            startRateAppActivity(arguments)
        } else {
            contextProvider().startActivity(
                appStoreIntentProvider().setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        return newEmptyResult()
    }

    private fun startRateAppActivity(arguments: ActionArguments) {
        val context = contextProvider()
        val argMap = arguments.value.toJsonValue().optMap()

        val intent = Intent(SHOW_RATE_APP_INTENT_ACTION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .setPackage(context.packageName)

        argMap.optionalField<String>(TITLE_KEY)?.let {
            intent.putExtra(TITLE_KEY, it)
        }

        argMap.optionalField<String>(BODY_KEY)?.let {
            intent.putExtra(BODY_KEY, it)
        }

        context.startActivity(intent)
    }

    /**
     * Checks if the argument's value can be parsed and ensures the situation is neither
     * [Action.Situation.PUSH_RECEIVED], nor [Action.Situation.BACKGROUND_NOTIFICATION_ACTION_BUTTON].
     *
     * @param arguments The action arguments.
     * @return `true` if the action can perform with the arguments,
     * otherwise `false`.
     */
    override fun acceptsArguments(arguments: ActionArguments): Boolean {
        return when (arguments.situation) {
            Situation.PUSH_RECEIVED,
            Situation.BACKGROUND_NOTIFICATION_ACTION_BUTTON -> {
                false
            }
            else -> true
        }
    }

    override fun shouldRunOnMainThread(): Boolean {
        return true
    }

    public companion object {

        /**
         * Default registry name
         */
        public const val DEFAULT_REGISTRY_NAME: String = "rate_app_action"

        /**
         * Default registry short name
         */
        public const val DEFAULT_REGISTRY_SHORT_NAME: String = "^ra"

        /**
         * Key for the showing link prompt option.
         */
        public const val SHOW_LINK_PROMPT_KEY: String = "show_link_prompt"

        /**
         * Key to define the app review link prompt's title when providing the action's value as a map.
         */
        public const val TITLE_KEY: String = "title"

        /**
         * Key to define the app review link prompt's body when providing the action's value as a map.
         */
        public const val BODY_KEY: String = "body"

        /**
         * Intent action for linking directly to store review page or displaying a rating link prompt
         * with the option of opening the review page link.
         */
        public const val SHOW_RATE_APP_INTENT_ACTION: String =
            "com.urbanairship.actions.SHOW_RATE_APP_INTENT_ACTION"
    }
}

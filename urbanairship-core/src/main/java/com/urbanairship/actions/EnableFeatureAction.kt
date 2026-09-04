/* Copyright Airship and Contributors */
package com.urbanairship.actions

import com.urbanairship.Airship
import com.urbanairship.json.JsonException
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionsManager

/**
 * An action that enables features. Running the action with value [FEATURE_LOCATION] or [FEATURE_BACKGROUND_LOCATION]
 * will prompt the user for permissions before enabling.
 *
 *
 * Accepted situations: [Action.Situation.PUSH_OPENED], [Action.Situation.WEB_VIEW_INVOCATION],
 * [Action.Situation.MANUAL_INVOCATION], [Action.Situation.AUTOMATION],
 * and [Action.Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON].
 *
 *
 * Accepted argument value - [FEATURE_USER_NOTIFICATIONS], [FEATURE_BACKGROUND_LOCATION],
 * [FEATURE_LOCATION], or any [Permission] value, e.g. "camera" or "photo_library".
 *
 *
 * Only [Permission.DISPLAY_NOTIFICATIONS] has a built-in delegate. Prompting for any other
 * permission requires the app to register a [com.urbanairship.permission.PermissionDelegate]
 * with [PermissionsManager], otherwise the prompt resolves to
 * [com.urbanairship.permission.PermissionStatus.NOT_DETERMINED] and nothing is shown.
 *
 *
 * Result value: `true` if the feature was enabled, otherwise `false`.
 *
 *
 * Default Registration Names: [DEFAULT_NAMES]
 */
public class EnableFeatureAction @JvmOverloads public constructor(
    permissionsManagerProvider: () -> PermissionsManager = { Airship.permissionsManager }
) : PromptPermissionAction(permissionsManagerProvider) {

    @Throws(JsonException::class, IllegalArgumentException::class)
    public override fun parseArg(arguments: ActionArguments): Args {
        val feature = arguments.value.toJsonValue().requireString()

        return when (feature) {
            FEATURE_BACKGROUND_LOCATION,
            FEATURE_LOCATION -> {
                Args(Permission.LOCATION, enableAirshipUsage = true, fallbackSystemSettings = true)
            }

            FEATURE_USER_NOTIFICATIONS -> {
                Args(Permission.DISPLAY_NOTIFICATIONS,
                    enableAirshipUsage = true, fallbackSystemSettings = true
                )
            }
            else -> {
                // Permissions added after these three are named by their Permission value,
                // so no per-permission argument constant is needed.
                val permission = Permission.entries.firstOrNull { it.value == feature.lowercase() }
                if (permission != null) {
                    Args(permission, enableAirshipUsage = true, fallbackSystemSettings = true)
                } else {
                    super.parseArg(arguments)
                }
            }
        }
    }

    override fun onStart(arguments: ActionArguments) {
        super.onStart(arguments)

        if (FEATURE_BACKGROUND_LOCATION.lowercase() != arguments.value.getString("").lowercase()) {
            return
        }
    }

    public companion object {

        /**
         * Default action names.
         */
        public val DEFAULT_NAMES: Set<String> = setOf("enable_feature", "^ef")

        /**
         * Action value to enable user notifications. See [com.urbanairship.push.PushManager.setUserNotificationsEnabled]
         */
        public const val FEATURE_USER_NOTIFICATIONS: String = "user_notifications"

        /**
         * Action value to enable location.
         */
        public const val FEATURE_LOCATION: String = "location"

        /**
         * Action value to enable location with background updates.
         */
        public const val FEATURE_BACKGROUND_LOCATION: String = "background_location"
    }
}

/* Copyright Airship and Contributors */
package com.urbanairship.actions

import com.urbanairship.Airship
import com.urbanairship.base.Supplier
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
 * Accepted argument value - either [FEATURE_USER_NOTIFICATIONS], [FEATURE_BACKGROUND_LOCATION],
 * or [FEATURE_LOCATION].
 *
 *
 * Result value: `true` if the feature was enabled, otherwise `false`.
 *
 *
 * Default Registration Names: [DEFAULT_REGISTRY_NAME], [DEFAULT_REGISTRY_SHORT_NAME]
 */
public class EnableFeatureAction @JvmOverloads public constructor(
    permissionsManagerSupplier: Supplier<PermissionsManager> = object :
        Supplier<PermissionsManager> {
        override fun get(): PermissionsManager {
            return Airship.shared().permissionsManager
        }
    }
) : PromptPermissionAction(permissionsManagerSupplier) {

    @Throws(JsonException::class, IllegalArgumentException::class)
    public override fun parseArg(arguments: ActionArguments): Args {
        val feature = arguments.value.toJsonValue().requireString()

        return when (feature) {
            FEATURE_BACKGROUND_LOCATION,
            FEATURE_LOCATION -> {
                Args(Permission.LOCATION, true, true)
            }

            FEATURE_USER_NOTIFICATIONS -> {
                Args(Permission.DISPLAY_NOTIFICATIONS, true, true)
            }
            else -> super.parseArg(arguments)
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
         * Default registry name
         */
        public const val DEFAULT_REGISTRY_NAME: String = "enable_feature"

        /**
         * Default registry short name
         */
        public const val DEFAULT_REGISTRY_SHORT_NAME: String = "^ef"

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

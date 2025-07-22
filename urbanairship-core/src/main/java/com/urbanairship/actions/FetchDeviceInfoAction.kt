/* Copyright Airship and Contributors */
package com.urbanairship.actions

import com.urbanairship.UAirship
import com.urbanairship.actions.ActionResult.Companion.newResult
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

/**
 * Action to fetch a map of device properties.
 *
 *
 * Accepted situations: all.
 *
 *
 * Accepted argument value - none.
 *
 *
 * Result value: [JsonMap] containing the device's channel ID, named user ID, push opt-in status,
 * location enabled status, and tags. An example response as JSON:
 * ```
 * {
 *  "channel_id": "9c36e8c7-5a73-47c0-9716-99fd3d4197d5",
 *  "push_opt_in": true,
 *  "location_enabled": true,
 *  "named_user": "cool_user",
 *  "tags": ["tag1", "tag2, "tag3"]
 * }
 * ```
 *
 *
 * Default Registration Names: [DEFAULT_REGISTRY_NAME], [DEFAULT_REGISTRY_SHORT_NAME]
 *
 *
 * Default Registration Predicate: only accepts [Action.Situation.WEB_VIEW_INVOCATION]
 * and [Action.Situation.MANUAL_INVOCATION]
 */
public class FetchDeviceInfoAction public constructor() : Action() {

    override fun perform(arguments: ActionArguments): ActionResult {

        val properties = JsonMap.newBuilder()
            .put(CHANNEL_ID_KEY, UAirship.shared().channel.id)
            .put(PUSH_OPT_IN_KEY, UAirship.shared().pushManager.isOptIn)
            .putOpt(NAMED_USER_ID_KEY, UAirship.shared().contact.namedUserId)

        val tags = UAirship.shared().channel.tags
        if (tags.isNotEmpty()) {
            properties.put(TAGS_KEY, JsonValue.wrapOpt(tags))
        }

        return newResult(ActionValue(properties.build().toJsonValue()))
    }

    /**
     * Default [FetchDeviceInfoAction] predicate.
     */
    public class FetchDeviceInfoPredicate public constructor() : ActionRegistry.Predicate {

        override fun apply(arguments: ActionArguments): Boolean {
            return when(arguments.situation) {
                Situation.WEB_VIEW_INVOCATION,
                Situation.MANUAL_INVOCATION -> true
                else -> false
            }
        }
    }

    public companion object {

        /**
         * Default registry name
         */
        public const val DEFAULT_REGISTRY_NAME: String = "fetch_device_info"

        /**
         * Default registry short name
         */
        public const val DEFAULT_REGISTRY_SHORT_NAME: String = "^fdi"

        /**
         * Channel ID response key.
         */
        public const val CHANNEL_ID_KEY: String = "channel_id"

        /**
         * Named user response key.
         */
        public const val NAMED_USER_ID_KEY: String = "named_user"

        /**
         * Tags response key.
         */
        public const val TAGS_KEY: String = "tags"

        /**
         * Push opt-in response key.
         */
        public const val PUSH_OPT_IN_KEY: String = "push_opt_in"
    }
}

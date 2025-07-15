/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.os.Bundle
import com.urbanairship.actions.Action.Situation

/**
 * Container for the argument data passed to an [com.urbanairship.actions.Action].
 */
public class ActionArguments @JvmOverloads public constructor(
    public val situation: Situation,
    public val value: ActionValue = ActionValue(),
    public val metadata: Bundle = Bundle()
) {

    override fun toString(): String {
        return "ActionArguments { situation: $situation, value: $value, metadata: $metadata }"
    }

    public companion object {

        /**
         * Metadata when running an action from the JavaScript interface with an associated RichPushMessage.
         * The value is stored as a String.
         */
        public const val RICH_PUSH_ID_METADATA: String = "com.urbanairship.RICH_PUSH_ID_METADATA"

        /**
         * Metadata attached to action arguments when running actions from a push message.
         * The value is stored as a [com.urbanairship.push.PushMessage].
         */
        public const val PUSH_MESSAGE_METADATA: String = "com.urbanairship.PUSH_MESSAGE"

        /**
         * Metadata attached to action argument when running actions from a [com.urbanairship.push.notifications.NotificationActionButton]
         * with [com.urbanairship.push.notifications.LocalizableRemoteInput].
         */
        public const val REMOTE_INPUT_METADATA: String = "com.urbanairship.REMOTE_INPUT"

        /**
         * Metadata attached to action arguments when running scheduled actions from Action Automation.
         */
        public const val ACTION_SCHEDULE_ID_METADATA: String = "com.urbanairship.ACTION_SCHEDULE_ID"

        /**
         * Metadata attached to action arguments when triggering an action from by name.
         * The value is stored as a String.
         */
        public const val REGISTRY_ACTION_NAME_METADATA: String =
            "com.urbanairship.REGISTRY_ACTION_NAME"
    }
}

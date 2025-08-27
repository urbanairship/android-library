/* Copyright Airship and Contributors */
package com.urbanairship.push.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import com.urbanairship.UALog
import com.urbanairship.Airship
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

/**
 * Notification builder extender to add the wearable overrides defined by a [PushMessage].
 */
public class WearableNotificationExtender public constructor(
    context: Context,
    private val arguments: NotificationArguments
) : NotificationCompat.Extender {

    private val context = context.applicationContext

    override fun extend(builder: NotificationCompat.Builder): NotificationCompat.Builder {
        val wearablePayload = arguments.message.wearablePayload ?: return builder

        val wearableJson = try {
            JsonValue.parseString(wearablePayload).optMap()
        } catch (e: JsonException) {
            UALog.e(e, "Failed to parse wearable payload.")
            return builder
        }

        val extender = NotificationCompat.WearableExtender()

        val actionGroupId = wearableJson.opt(INTERACTIVE_TYPE_KEY).string
        var actionsPayload: String? = wearableJson.opt(INTERACTIVE_ACTIONS_KEY).toString()
        if (actionsPayload.isNullOrEmpty()) {
            actionsPayload = arguments.message.interactiveActionsPayload
        }

        if (!actionGroupId.isNullOrEmpty()) {
            Airship.shared().pushManager
                .getNotificationActionGroup(actionGroupId)
                ?.let {
                    extender.addActions(it.createAndroidActions(context, arguments, actionsPayload))
                }
        }

        builder.extend(extender)

        return builder
    }

    public companion object {

        public const val TITLE_KEY: String = "title"
        public const val ALERT_KEY: String = "alert"

        // Wearable
        public const val INTERACTIVE_TYPE_KEY: String = "interactive_type"
        public const val INTERACTIVE_ACTIONS_KEY: String = "interactive_actions"
    }
}

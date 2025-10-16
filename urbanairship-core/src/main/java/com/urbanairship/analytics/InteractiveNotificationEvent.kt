/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonMap
import com.urbanairship.push.NotificationActionButtonInfo
import com.urbanairship.push.NotificationInfo

/**
 * An event that captures information regarding an interactive notification action open.
 *
 * @hide
 */
internal class InteractiveNotificationEvent(
    notificationInfo: NotificationInfo,
    buttonInfo: NotificationActionButtonInfo
) : Event() {

    private val sendId = notificationInfo.message.sendId
    private val buttonGroupId = notificationInfo.message.interactiveNotificationType
    private val buttonId = buttonInfo.buttonId
    private val buttonDescription = buttonInfo.description
    private val isForeground = buttonInfo.isForeground
    private val remoteInput = buttonInfo.remoteInput

    override val type: EventType = EventType.INTERACTIVE_NOTIFICATION_ACTION

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun getEventData(context: Context, conversionData: ConversionData): JsonMap {
        val builder = JsonMap.newBuilder()
            .put(SEND_ID_KEY, sendId)
            .put(BUTTON_GROUP_KEY, buttonGroupId)
            .put(BUTTON_ID_KEY, buttonId)
            .put(BUTTON_DESCRIPTION_KEY, buttonDescription)
            .put(FOREGROUND_KEY, isForeground)

        if (remoteInput?.isEmpty == false) {
            val input = JsonMap.newBuilder()
            remoteInput.keySet().forEach {
                input.put(it, remoteInput.getString(it))
            }
            builder.put(USER_INPUT, input.build())
        }

        return builder.build()
    }

    companion object {
        private const val SEND_ID_KEY = "send_id"
        private const val BUTTON_GROUP_KEY = "button_group"
        private const val BUTTON_ID_KEY = "button_id"
        private const val BUTTON_DESCRIPTION_KEY = "button_description"
        private const val FOREGROUND_KEY = "foreground"
        private const val USER_INPUT = "user_input"
    }
}

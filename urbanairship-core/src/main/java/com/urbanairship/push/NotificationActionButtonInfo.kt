package com.urbanairship.push

import android.content.Intent
import android.os.Bundle
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.core.app.RemoteInput

/**
 * Notification action button info.
 */
public class NotificationActionButtonInfo internal constructor(
    /**
     * @property buttonId The button's ID.
     */
    public val buttonId: String,
    /**
     * @property isForeground If the button should trigger a foreground action or not.
     */
    public val isForeground: Boolean,
    /**
     * @property remoteInput Remote input associated with the notification action. Only available if the action
     * button defines [com.urbanairship.push.notifications.LocalizableRemoteInput]
     * and the button was triggered from an Android Wear device or Android N.
     */
    public val remoteInput: Bundle?,
    /**
     * @property description The action's description.
     */
    public val description: String?
) {

    override fun toString(): String {
        return "NotificationActionButtonInfo{buttonId='$buttonId', isForeground=$isForeground, " +
                "remoteInput=$remoteInput, description='$description'}"
    }

    public companion object {

        @JvmStatic
        public fun fromIntent(intent: Intent): NotificationActionButtonInfo? {
            val buttonId = intent.getStringExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID)
                ?: return null

            return NotificationActionButtonInfo(
                buttonId = buttonId,
                isForeground = intent.getBooleanExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, true),
                remoteInput = RemoteInput.getResultsFromIntent(intent),
                description = intent.getStringExtra(PushManager.EXTRA_NOTIFICATION_ACTION_BUTTON_DESCRIPTION)
            )
        }
    }
}

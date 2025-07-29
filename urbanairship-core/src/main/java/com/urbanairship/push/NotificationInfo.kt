package com.urbanairship.push

import android.content.Intent
import androidx.annotation.RestrictTo

/**
 * Notification info.
 *
 * @property message The push message.
 * @property notificationId The notification's Id.
 * @property notificationTag Returns the notification's tag.
 */
public class NotificationInfo @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public constructor(
    @JvmField public val message: PushMessage,
    @JvmField public val notificationId: Int,
    public val notificationTag: String?
) {

    override fun toString(): String {
        return "NotificationInfo{alert=${message.alert}, notificationId=$notificationId, " +
                "notificationTag='$notificationTag'}"
    }

    public companion object {
        public fun fromIntent(intent: Intent?): NotificationInfo? {
            val unwrappedIntent = intent ?: return null
            val message = PushMessage.fromIntent(unwrappedIntent) ?: return null

            val id = unwrappedIntent.getIntExtra(PushManager.EXTRA_NOTIFICATION_ID, -1)
            val tag = unwrappedIntent.getStringExtra(PushManager.EXTRA_NOTIFICATION_TAG)
            return NotificationInfo(message, id, tag)
        }
    }
}

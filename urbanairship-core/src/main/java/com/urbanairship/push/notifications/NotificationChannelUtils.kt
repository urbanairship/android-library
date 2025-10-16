/* Copyright Airship and Contributors */
package com.urbanairship.push.notifications

import android.app.Notification
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationManagerCompat
import com.urbanairship.UALog
import com.urbanairship.Airship

/**
 * Notification channel utils.
 *
 * @hide
 */
internal object NotificationChannelUtils {
    /**
     * Helper method to apply channel compat settings to a notification on Pre-O devices.
     *
     * @param notification The notification.
     * @param channelCompat The notification channel compat.
     */
    @Suppress("deprecation")
    fun applyLegacySettings(
        notification: Notification,
        channelCompat: NotificationChannelCompat
    ) {
        notification.priority = priorityForImportance(channelCompat.importance)

        // If it's lower than default importance, disable sound, light, and vibration
        if (channelCompat.importance < NotificationManagerCompat.IMPORTANCE_DEFAULT) {
            notification.vibrate = null
            notification.sound = null
            notification.ledARGB = 0
            notification.flags = notification.flags and Notification.FLAG_SHOW_LIGHTS.inv()
            notification.defaults = 0
            return
        }

        if (channelCompat.sound != null) {
            notification.sound = channelCompat.sound
            notification.defaults = notification.defaults and Notification.DEFAULT_SOUND.inv()
        }

        if (channelCompat.shouldShowLights()) {
            notification.flags = notification.flags or Notification.FLAG_SHOW_LIGHTS
            if (channelCompat.lightColor != 0) {
                notification.ledARGB = channelCompat.lightColor
                notification.defaults = notification.defaults and Notification.DEFAULT_LIGHTS.inv()
            } else {
                notification.defaults = notification.defaults or Notification.DEFAULT_LIGHTS
            }
        }

        if (channelCompat.shouldVibrate()) {
            if (channelCompat.vibrationPattern != null) {
                notification.vibrate = channelCompat.vibrationPattern
                notification.defaults = notification.defaults and Notification.DEFAULT_VIBRATE.inv()
            } else {
                notification.defaults = notification.defaults or Notification.DEFAULT_VIBRATE
            }
        }
    }

    /**
     * Converts importance to priority.
     *
     * @note importance and priority do no perfectly overlap. In particular there is no equivalent
     * to [NotificationManagerCompat.IMPORTANCE_NONE], and [NotificationManagerCompat.IMPORTANCE_UNSPECIFIED]
     * is ignored because the public docs say "This value is for persisting preferences,
     * and should never be associated with an actual notification". The latter case is unlikely
     * to be encountered in practice, and the best option for the former is to map to
     * [Notification.PRIORITY_LOW], which is the default here.
     *
     * @param importance The importance.
     * @return The priority.
     */
    @Suppress("deprecation")
    private fun priorityForImportance(importance: Int): Int {
        return when (importance) {
            NotificationManagerCompat.IMPORTANCE_DEFAULT -> Notification.PRIORITY_DEFAULT
            NotificationManagerCompat.IMPORTANCE_HIGH -> Notification.PRIORITY_HIGH
            NotificationManagerCompat.IMPORTANCE_LOW -> Notification.PRIORITY_LOW
            NotificationManagerCompat.IMPORTANCE_MAX -> Notification.PRIORITY_MAX
            NotificationManagerCompat.IMPORTANCE_MIN -> Notification.PRIORITY_MIN
            else -> Notification.PRIORITY_LOW
        }
    }
}

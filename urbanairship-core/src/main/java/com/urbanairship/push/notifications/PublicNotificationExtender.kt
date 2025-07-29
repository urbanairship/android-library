/* Copyright Airship and Contributors */
package com.urbanairship.push.notifications

import android.content.Context
import android.graphics.BitmapFactory
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

/**
 * Notification builder extender to add the public notification defined by a [PushMessage].
 */
public class PublicNotificationExtender public constructor(
    private val context: Context,
    private val arguments: NotificationArguments
) : NotificationCompat.Extender {

    private var accentColor = 0
    private var smallIconId = context.applicationInfo.icon
    private var largeIconId = 0

    /**
     * Sets the accent color.
     *
     * @param accentColor The notification's accent color.
     * @return The [PublicNotificationExtender] to chain calls.
     */
    public fun setAccentColor(@ColorInt accentColor: Int): PublicNotificationExtender {
        return this.also { it.accentColor = accentColor }
    }

    /**
     * Sets the small icon.
     *
     * @param smallIcon The small icon.
     * @return The [PublicNotificationExtender] to chain calls.
     */
    public fun setSmallIcon(@DrawableRes smallIcon: Int): PublicNotificationExtender {
        return this.also { it.smallIconId = smallIcon }
    }

    /**
     * Sets the large icon.
     *
     * @param largeIcon The large icon.
     * @return The [PublicNotificationExtender] to chain calls.
     */
    public fun setLargeIcon(@DrawableRes largeIcon: Int): PublicNotificationExtender {
        return this.also { it.largeIconId = largeIcon }
    }

    override fun extend(builder: NotificationCompat.Builder): NotificationCompat.Builder {
        if (arguments.message.publicNotificationPayload.isNullOrEmpty()) {
            return builder
        }

        try {
            val jsonMap = JsonValue.parseString(arguments.message.publicNotificationPayload).optMap()

            val publicBuilder = NotificationCompat.Builder(context, arguments.notificationChannelId)
                .setContentTitle(jsonMap.opt(TITLE_KEY).optString())
                .setContentText(jsonMap.opt(ALERT_KEY).optString())
                .setColor(accentColor)
                .setAutoCancel(true)
                .setSmallIcon(smallIconId)

            if (largeIconId != 0) {
                publicBuilder.setLargeIcon(
                    BitmapFactory.decodeResource(context.resources, largeIconId)
                )
            }

            jsonMap[SUMMARY_KEY]?.string?.let { publicBuilder.setSubText(it) }

            builder.setPublicVersion(publicBuilder.build())
        } catch (e: JsonException) {
            UALog.e(e, "Failed to parse public notification.")
        }

        return builder
    }

    public companion object {

        public const val TITLE_KEY: String = "title"
        public const val SUMMARY_KEY: String = "summary"
        public const val ALERT_KEY: String = "alert"
    }
}

/* Copyright Airship and Contributors */
package com.urbanairship.push.notifications

import android.app.Notification
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.push.PushMessage
import com.urbanairship.util.NotificationIdGenerator

/**
 * Default notification provider.
 */
public open class AirshipNotificationProvider public constructor(
    context: Context,
    configOptions: AirshipConfigOptions
) : NotificationProvider {

    /**
     * The default notification title string resource.
     */
    @StringRes
    public var defaultTitle: Int = context.applicationInfo.labelRes

    /**
     * The small icon drawable resource.
     */
    @DrawableRes
    public var smallIcon: Int

    /**
     * The large icon drawable resource.
     */
    @JvmField
    @DrawableRes
    public var largeIcon: Int

    /**
     * The default accent color.
     */
    @ColorInt
    public var defaultAccentColor: Int

    /**
     * The default notification channel.
     */
    public var defaultNotificationChannelId: String

    init {
        this.smallIcon = configOptions.notificationIcon
        this.largeIcon = configOptions.notificationLargeIcon
        this.defaultAccentColor = configOptions.notificationAccentColor

        this.defaultNotificationChannelId = configOptions.notificationChannel
            ?: NotificationProvider.DEFAULT_NOTIFICATION_CHANNEL

        if (this.smallIcon == 0) {
            smallIcon = context.applicationInfo.icon
            if (smallIcon == 0) {
                smallIcon = context.resources.getIdentifier(
                    DEFAULT_AIRSHIP_NOTIFICATION_ICON, "drawable", context.packageName
                )
            }
        }

        this.defaultTitle = context.applicationInfo.labelRes
    }

    override fun onCreateNotificationArguments(
        context: Context,
        message: PushMessage
    ): NotificationArguments {
        val requestedChannelId = message.getNotificationChannel(defaultNotificationChannelId)
        val activeChannelId = NotificationChannelUtils.getActiveChannel(
            requestedChannelId, NotificationProvider.DEFAULT_NOTIFICATION_CHANNEL
        )

        return NotificationArguments.newBuilder(message)
            .setNotificationChannelId(activeChannelId)
            .setNotificationId(message.notificationTag, getNextId(context, message))
            .build()
    }

    override fun onCreateNotification(
        context: Context,
        arguments: NotificationArguments
    ): NotificationResult {
        if (arguments.message.alert.isNullOrEmpty()) {
            return NotificationResult.cancel()
        }

        val message = arguments.message

        val builder = NotificationCompat.Builder(context, arguments.notificationChannelId)
            .setContentTitle(getTitle(context, message))
            .setContentText(message.alert)
            .setAutoCancel(true)
            .setLocalOnly(message.isLocalOnly)
            .setColor(message.getIconColor(defaultAccentColor))
            .setSmallIcon(message.getIcon(context, smallIcon))
            .setPriority(message.priority)
            .setCategory(message.category)
            .setVisibility(message.visibility)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        val largeIcon = largeIcon
        if (largeIcon != 0) {
            builder.setLargeIcon(BitmapFactory.decodeResource(context.resources, largeIcon))
        }

        message.summary?.let { builder.setSubText(it) }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            applyDeprecatedSettings(context, message, builder)
        }

        val notification = onExtendBuilder(context, builder, arguments).build()
        return NotificationResult.notification(notification)
    }

    @Suppress("deprecation")
    private fun applyDeprecatedSettings(
        context: Context,
        message: PushMessage,
        builder: NotificationCompat.Builder
    ) {
        var defaults = Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE

        message.getSound(context)?.let {
            builder.setSound(it)

            // Remove the Notification.DEFAULT_SOUND flag
            defaults = defaults and Notification.DEFAULT_SOUND.inv()
        }

        builder.setDefaults(defaults)
    }

    override fun onNotificationCreated(
        context: Context,
        notification: Notification,
        arguments: NotificationArguments
    ) { }

    /**
     * Override this method to extend the notification builder.
     *
     * The default method behavior applies extends the builder with [PublicNotificationExtender],
     * [WearableNotificationExtender], [ActionsNotificationExtender], and
     * [StyleNotificationExtender].
     *
     * @param context The application context.
     * @param builder The notification builder.
     * @param arguments The notification arguments.
     * @return The notification builder.
     */
    protected open fun onExtendBuilder(
        context: Context,
        builder: NotificationCompat.Builder,
        arguments: NotificationArguments
    ): NotificationCompat.Builder {
        val message = arguments.message

        // Public notification
        builder.extend(
            PublicNotificationExtender(context, arguments)
                .setAccentColor(defaultAccentColor)
                .setLargeIcon(largeIcon)
                .setSmallIcon(message.getIcon(context, smallIcon))
        )

        // Wearable support
        builder.extend(WearableNotificationExtender(context, arguments))

        // Notification action buttons
        builder.extend(ActionsNotificationExtender(context, arguments))

        // Styles
        val defaultStyle = NotificationCompat.BigTextStyle().bigText(arguments.message.alert)

        builder.extend(
            StyleNotificationExtender(context, message).setDefaultStyle(defaultStyle)
        )

        return builder
    }

    /**
     * Gets the next notification Id.
     *
     * @param context The application context.
     * @param message The push message.
     * @return The notification Id.
     */
    protected fun getNextId(context: Context, message: PushMessage): Int {
        if (message.notificationTag != null) {
            return TAG_NOTIFICATION_ID
        }

        return NotificationIdGenerator.nextID()
    }

    /**
     * Gets the notification title.
     *
     * @param context The application context.
     * @param message The push message.
     * @return The notification title.
     */
    protected fun getTitle(context: Context, message: PushMessage): String? {
        if (message.title != null) {
            return message.title
        }

        if (defaultTitle != 0) {
            return context.getString(defaultTitle)
        }

        return null
    }

    public companion object {

        /**
         * Default Notification ID when the [PushMessage] defines a notification tag.
         */
        public const val TAG_NOTIFICATION_ID: Int = 100

        /**
         * Default Airship notification icon.
         */
        public const val DEFAULT_AIRSHIP_NOTIFICATION_ICON: String = "ua_default_ic_notification"
    }
}

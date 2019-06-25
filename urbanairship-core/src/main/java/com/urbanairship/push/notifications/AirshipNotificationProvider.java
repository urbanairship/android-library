/* Copyright Airship and Contributors */

package com.urbanairship.push.notifications;

import android.app.Notification;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Build;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.NotificationIdGenerator;
import com.urbanairship.util.UAStringUtil;

/**
 * Default notification provider.
 */
public class AirshipNotificationProvider implements NotificationProvider {

    /**
     * Default Notification ID when the {@link PushMessage} defines a notification tag.
     */
    public static final int TAG_NOTIFICATION_ID = 100;

    @StringRes
    private int titleId;

    @DrawableRes
    private int smallIconId;

    @DrawableRes
    private int largeIcon;

    @ColorInt
    private int accentColor;

    @NonNull
    private String defaultNotificationChannelId;

    /**
     * Set the default notification title.
     *
     * @param titleId The title string resource. A value of 0 will result in no default title.
     */
    public void setDefaultTitle(@StringRes int titleId) {
        this.titleId = titleId;
    }

    /**
     * Get the default notification title string resource.
     *
     * @return The title string resource.
     */
    @StringRes
    public int getDefaultTitle() {
        return titleId;
    }

    /**
     * Set the notification small icon.
     *
     * @param smallIconId The small icon drawable resource.
     */
    public void setSmallIcon(@DrawableRes int smallIconId) {
        this.smallIconId = smallIconId;
    }

    /**
     * Get the small icon drawable resource.
     *
     * Defaults to the application's icon.
     *
     * @return The small icon drawable resource.
     */
    @DrawableRes
    public int getSmallIcon() {
        return smallIconId;
    }

    /**
     * Set the notification large icon.
     *
     * @param largeIcon The large icon drawable resource.
     */
    public void setLargeIcon(@DrawableRes int largeIcon) {
        this.largeIcon = largeIcon;
    }

    /**
     * Get the large icon drawable resource.
     *
     * @return The large icon drawable resource.
     */
    @DrawableRes
    public int getLargeIcon() {
        return largeIcon;
    }

    /**
     * Set the default notification accent color.
     *
     * @param accentColor The accent color.
     */
    public void setDefaultAccentColor(@ColorInt int accentColor) {
        this.accentColor = accentColor;
    }

    /**
     * Get the default accent color.
     *
     * @return The accent color as an int.
     */
    @ColorInt
    public int getDefaultAccentColor() {
        return accentColor;
    }

    /**
     * Sets the default notification channel.
     *
     * @param channel The default notification channel.
     */
    public void setDefaultNotificationChannelId(@NonNull String channel) {
        this.defaultNotificationChannelId = channel;
    }

    /**
     * Gets the default notification channel.
     *
     * @return The default notification channel.
     */
    @NonNull
    public String getDefaultNotificationChannelId() {
        return this.defaultNotificationChannelId;
    }

    public AirshipNotificationProvider(@NonNull Context context, @NonNull AirshipConfigOptions configOptions) {
        this.titleId = context.getApplicationInfo().labelRes;
        this.smallIconId = configOptions.notificationIcon;
        this.largeIcon = configOptions.notificationLargeIcon;
        this.accentColor = configOptions.notificationAccentColor;

        if (configOptions.notificationChannel != null) {
            this.defaultNotificationChannelId = configOptions.notificationChannel;
        } else {
            this.defaultNotificationChannelId = NotificationProvider.DEFAULT_NOTIFICATION_CHANNEL;
        }

        if (this.smallIconId == 0) {
            smallIconId = context.getApplicationInfo().icon;
        }

        this.titleId = context.getApplicationInfo().labelRes;
    }

    @NonNull
    @Override
    public NotificationArguments onCreateNotificationArguments(@NonNull Context context, @NonNull PushMessage message) {
        String requestedChannelId = message.getNotificationChannel(getDefaultNotificationChannelId());
        String activeChannelId = NotificationChannelUtils.getActiveChannel(requestedChannelId, DEFAULT_NOTIFICATION_CHANNEL);

        return NotificationArguments.newBuilder(message)
                                    .setNotificationChannelId(activeChannelId)
                                    .setNotificationId(message.getNotificationTag(), getNextId(context, message))
                                    .build();
    }

    @NonNull
    @Override
    public NotificationResult onCreateNotification(@NonNull Context context, @NonNull NotificationArguments arguments) {
        if (UAStringUtil.isEmpty(arguments.getMessage().getAlert())) {
            return NotificationResult.cancel();
        }

        PushMessage message = arguments.getMessage();
        String title = getTitle(context, message);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, arguments.getNotificationChannelId())
                .setContentTitle(title)
                .setContentText(message.getAlert())
                .setAutoCancel(true)
                .setLocalOnly(message.isLocalOnly())
                .setColor(message.getIconColor(getDefaultAccentColor()))
                .setSmallIcon(message.getIcon(context, getSmallIcon()))
                .setPriority(message.getPriority())
                .setCategory(message.getCategory())
                .setVisibility(message.getVisibility())
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        int largeIcon = getLargeIcon();
        if (largeIcon != 0) {
            builder.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), largeIcon));
        }

        if (message.getSummary() != null) {
            builder.setSubText(message.getSummary());
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            int defaults = Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE;
            if (message.getSound(context) != null) {
                builder.setSound(message.getSound(context));

                // Remove the Notification.DEFAULT_SOUND flag
                defaults &= ~Notification.DEFAULT_SOUND;
            }
            builder.setDefaults(defaults);
        }

        Notification notification = onExtendBuilder(context, builder, arguments).build();
        return NotificationResult.notification(notification);
    }

    @Override
    public void onNotificationCreated(@NonNull Context context, @NonNull Notification notification, @NonNull NotificationArguments arguments) { }

    /**
     * Override this method to extend the notification builder.
     *
     * The default method behavior applies extends the builder with {@link PublicNotificationExtender},
     * {@link WearableNotificationExtender}, {@link ActionsNotificationExtender}, and
     * {@link StyleNotificationExtender}.
     *
     * @param context The application context.
     * @param builder The notification builder.
     * @param arguments The notification arguments.
     * @return The notification builder.
     */
    @NonNull
    protected NotificationCompat.Builder onExtendBuilder(@NonNull Context context,
                                                         @NonNull NotificationCompat.Builder builder,
                                                         @NonNull NotificationArguments arguments) {

        PushMessage message = arguments.getMessage();

        // Public notification
        builder.extend(new PublicNotificationExtender(context, arguments)
                .setAccentColor(getDefaultAccentColor())
                .setLargeIcon(getLargeIcon())
                .setSmallIcon(message.getIcon(context, getSmallIcon())));

        // Wearable support
        builder.extend(new WearableNotificationExtender(context, arguments));

        // Notification action buttons
        builder.extend(new ActionsNotificationExtender(context, arguments));

        // Styles
        NotificationCompat.Style defaultStyle = new NotificationCompat.BigTextStyle().bigText(arguments.getMessage().getAlert());
        builder.extend(new StyleNotificationExtender(context, message)
                .setDefaultStyle(defaultStyle));

        return builder;
    }

    /**
     * Gets the next notification Id.
     *
     * @param context The application context.
     * @param message The push message.
     * @return The notification Id.
     */
    protected int getNextId(@NonNull Context context, @NonNull PushMessage message) {
        if (message.getNotificationTag() != null) {
            return TAG_NOTIFICATION_ID;
        }

        return NotificationIdGenerator.nextID();
    }

    /**
     * Gets the notification title.
     *
     * @param context The application context.
     * @param message The push message.
     * @return The notification title.
     */
    @Nullable
    protected String getTitle(@NonNull Context context, @NonNull PushMessage message) {
        if (message.getTitle() != null) {
            return message.getTitle();
        }

        if (titleId != 0) {
            return context.getString(titleId);
        }

        return null;
    }

}

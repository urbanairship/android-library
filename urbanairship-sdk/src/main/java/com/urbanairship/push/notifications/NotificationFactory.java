/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.notifications;

import android.app.Notification;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.NotificationCompat;

import com.urbanairship.AirshipReceiver;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.NotificationIdGenerator;
import com.urbanairship.util.UAStringUtil;


/**
 * Notification factory that provides a pathway for customizing the display of push notifications
 * in the Android <code>NotificationManager</code>.
 * <p/>
 * {@link DefaultNotificationFactory} is used by default and applies the big text style. For custom
 * layouts, see {@link CustomLayoutNotificationFactory}.
 */
public class NotificationFactory {

    private int titleId;
    private int smallIconId;
    private int largeIcon;
    private Uri sound = null;
    private int constantNotificationId = -1;
    private int accentColor = NotificationCompat.COLOR_DEFAULT;
    private int notificationDefaults = NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_VIBRATE;

    /**
     * Default Notification ID when the {@link PushMessage} defines a notification tag.
     */
    public static final int TAG_NOTIFICATION_ID = 100;

    private final Context context;

    /**
     * Default constructor.
     *
     * @param context The application context.
     */
    public NotificationFactory(@NonNull Context context) {
        this.context = context.getApplicationContext();
        titleId = context.getApplicationInfo().labelRes;
        smallIconId = context.getApplicationInfo().icon;
    }

    /**
     * Set the optional constant notification ID.
     * <p>
     * Only values greater than 0 will be used by default. Any negative value will
     * be considered invalid and the constant notification ID will be ignored.
     * <p>
     * By default, the constant notification ID will be used if the push message does not contain a tag.
     * In that case, {@link #TAG_NOTIFICATION_ID} will be used instead.
     *
     * @param id The integer ID as an int.
     */
    public NotificationFactory setConstantNotificationId(int id) {
        constantNotificationId = id;
        return this;
    }

    /**
     * Get the constant notification ID.
     *
     * @return The constant notification ID as an int.
     */
    public int getConstantNotificationId() {
        return constantNotificationId;
    }

    /**
     * Set the title used in the notification layout.
     *
     * @param titleId The title as an int. A value of -1 will not display a title. A value of 0 will
     * display the application name as the title. A string resource ID will display the specified
     * string as the title.
     */
    public void setTitleId(@StringRes int titleId) {
        this.titleId = titleId;
    }

    /**
     * Get the title used in the notification layout.
     *
     * @return The title as an int.
     */
    @StringRes
    public int getTitleId() {
        return titleId;
    }

    /**
     * Set the small icon used in the notification layout.
     *
     * @param smallIconId The small icon ID as an int.
     */
    public void setSmallIconId(@DrawableRes int smallIconId) {
        this.smallIconId = smallIconId;
    }

    /**
     * Get the small icon used in the notification layout.
     *
     * @return The small icon ID as an int.
     */
    @DrawableRes
    public int getSmallIconId() {
        return smallIconId;
    }

    /**
     * Set the sound played when the notification arrives.
     *
     * @param sound The sound as a Uri.
     */
    public void setSound(Uri sound) {
        this.sound = sound;
    }

    /**
     * Get the sound played when the notification arrives.
     *
     * @return The sound as a Uri.
     */
    public Uri getSound() {
        return sound;
    }

    /**
     * Set the large icon used in the notification layout.
     *
     * @param largeIcon The large icon ID as an int.
     */
    public void setLargeIcon(@DrawableRes int largeIcon) {
        this.largeIcon = largeIcon;
    }

    /**
     * Get the large icon used in the notification layout.
     *
     * @return The large icon ID as a int.
     */
    @DrawableRes
    public int getLargeIcon() {
        return largeIcon;
    }

    /**
     * Set the accent color used in the notification.
     *
     * @param accentColor The accent color of the main notification icon.
     */
    public void setColor(@ColorInt int accentColor) {
        this.accentColor = accentColor;
    }

    /**
     * Get the accent color used in the notification.
     *
     * @return The accent color as an int.
     */
    @ColorInt
    public int getColor() {
        return accentColor;
    }

    /**
     * Gets the default notification options.
     *
     * @return The default notification options.
     */
    public int getNotificationDefaultOptions() {
        return notificationDefaults;
    }

    /**
     * Sets the default notification options. Defaults to
     * {@code NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_VIBRATE}.
     *
     * @param defaults The default options.
     */
    public void setNotificationDefaultOptions(int defaults) {
        this.notificationDefaults = defaults;
    }

    /**
     * Gets the default title for the notification. If the {@link #getTitleId()} is 0,
     * the application label will be used, if greater than 0 the string will be fetched
     * from the resources, and if negative an empty String
     *
     * @return The default notification title.
     */
    protected String getTitle(@NonNull PushMessage message) {
        if (message.getTitle() != null) {
            return message.getTitle();
        }

        if (getTitleId() == 0) {
            return getContext().getPackageManager().getApplicationLabel(getContext().getApplicationInfo()).toString();
        } else if (getTitleId() > 0) {
            return getContext().getString(getTitleId());
        }

        return "";
    }

    /**
     * Gets application context.
     *
     * @return The application context.
     */
    @NonNull
    protected Context getContext() {
        return context;
    }

    /**
     * Creates a <code>Notification</code> for an incoming push message.
     * <p/>
     * In order to handle notification opens, the application should register a broadcast receiver
     * that extends {@link com.urbanairship.AirshipReceiver}. When the notification is opened
     * it will call {@link com.urbanairship.AirshipReceiver#onNotificationOpened(Context, AirshipReceiver.NotificationInfo)}
     * giving the application a chance to handle the notification open. If the broadcast receiver is not registered,
     * or {@code false} is returned, an open will be handled by either starting the launcher activity or
     * by sending the notification's content intent if it is present.
     *
     * @param message The push message.
     * @param notificationId The notification ID.
     * @return The notification to display, or <code>null</code> if no notification is desired.
     */
    @Nullable
    public Notification createNotification(@NonNull final PushMessage message, final int notificationId) {
        if (UAStringUtil.isEmpty(message.getAlert())) {
            return null;
        }

        NotificationCompat.Builder builder = createNotificationBuilder(message, notificationId, null);
        return builder.build();
    }

    /**
     * Creates a NotificationCompat.Builder with the default settings applied.
     *
     * @param message The PushMessage.
     * @param notificationId The notification id.
     * @param defaultStyle The default notification style.
     * @return A NotificationCompat.Builder.
     */
    protected NotificationCompat.Builder createNotificationBuilder(@NonNull PushMessage message, int notificationId, @Nullable NotificationCompat.Style defaultStyle) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext())
                .setContentTitle(getTitle(message))
                .setContentText(message.getAlert())
                .setAutoCancel(true)
                .setLocalOnly(message.isLocalOnly())
                .setColor(message.getIconColor(getColor()))
                .setSmallIcon(message.getIcon(context, getSmallIconId()))
                .setPriority(message.getPriority())
                .setCategory(message.getCategory())
                .setVisibility(message.getVisibility());

        int defaults = getNotificationDefaultOptions();
        if (message.getSound(getContext()) != null) {
            builder.setSound(message.getSound(getContext()));

            // Remove the Notification.DEFAULT_SOUND flag
            defaults &= ~Notification.DEFAULT_SOUND;
        } else if (getSound() != null) {
            builder.setSound(getSound());

            // Remove the Notification.DEFAULT_SOUND flag
            defaults &= ~Notification.DEFAULT_SOUND;
        }

        builder.setDefaults(defaults);

        if (getLargeIcon() > 0) {
            builder.setLargeIcon(BitmapFactory.decodeResource(getContext().getResources(), getLargeIcon()));
        }

        if (message.getSummary() != null) {
            builder.setSubText(message.getSummary());
        }

        // Public notification
        builder.extend(new PublicNotificationExtender(getContext(), message)
                .setAccentColor(getColor())
                .setLargeIcon(getLargeIcon())
                .setSmallIcon(getSmallIconId()));


        // Wearable support
        builder.extend(new WearableNotificationExtender(getContext(), message, notificationId));

        // Notification action buttons
        builder.extend(new ActionsNotificationExtender(getContext(), message, notificationId));

        // Styles
        builder.extend(new StyleNotificationExtender(getContext(), message)
                .setDefaultStyle(defaultStyle));

        return builder;
    }

    /**
     * Creates a notification ID based on the message and payload.
     * <p/>
     * This method could return a constant (to always replace the existing ID)
     * or a payload/message specific ID (to replace in cases where there are duplicates, for example)
     * or a random/sequential (to always add a new notification).
     * <p>
     * The default behavior returns {@link #TAG_NOTIFICATION_ID} if the push message contains a tag
     * (see {@link PushMessage#getNotificationTag()}). Otherwise it will either return {@link #getConstantNotificationId()}
     * if the constant notification id > 0, or it will return a random ID generated from {@link NotificationIdGenerator#nextID()}.
     *
     * @param pushMessage The push message.
     * @return An integer ID for the next notification.
     */
    public int getNextId(@NonNull PushMessage pushMessage) {
        if (pushMessage.getNotificationTag() != null) {
            return TAG_NOTIFICATION_ID;
        }

        if (constantNotificationId > 0) {
            return constantNotificationId;
        }

        return NotificationIdGenerator.nextID();
    }
}

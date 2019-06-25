/* Copyright Airship and Contributors */

package com.urbanairship.push.notifications;

import android.content.Context;
import android.graphics.BitmapFactory;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.UAStringUtil;

/**
 * Notification builder extender to add the public notification defined by a {@link PushMessage}.
 */
public class PublicNotificationExtender implements NotificationCompat.Extender {

    static final String TITLE_KEY = "title";
    static final String SUMMARY_KEY = "summary";
    static final String ALERT_KEY = "alert";

    private final Context context;
    private final NotificationArguments arguments;
    private int accentColor;
    private int smallIconId;
    private int largeIconId;

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param arguments The notification arguments.
     */
    public PublicNotificationExtender(@NonNull Context context, @NonNull NotificationArguments arguments) {
        this.context = context;
        this.arguments = arguments;
        this.smallIconId = context.getApplicationInfo().icon;
    }

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param message The push message.
     * @deprecated Use {{@link #PublicNotificationExtender(Context, NotificationArguments)} instead. To be removed
     * in SDK 11.
     */
    @Deprecated
    public PublicNotificationExtender(@NonNull Context context, @NonNull PushMessage message) {
        this(context, NotificationArguments.newBuilder(message).build());
    }

    /**
     * Sets the accent color.
     *
     * @param accentColor The notification's accent color.
     * @return The PublicNotificationExtender to chain calls.
     */
    @NonNull
    public PublicNotificationExtender setAccentColor(@ColorInt int accentColor) {
        this.accentColor = accentColor;
        return this;
    }

    /**
     * Sets the small icon.
     *
     * @param smallIcon The small icon.
     * @return The PublicNotificationExtender to chain calls.
     */
    @NonNull
    public PublicNotificationExtender setSmallIcon(@DrawableRes int smallIcon) {
        this.smallIconId = smallIcon;
        return this;
    }

    /**
     * Sets the large icon.
     *
     * @param largeIcon The large icon.
     * @return The PublicNotificationExtender to chain calls.
     */
    @NonNull
    public PublicNotificationExtender setLargeIcon(@DrawableRes int largeIcon) {
        this.largeIconId = largeIcon;
        return this;
    }

    @NonNull
    @Override
    public NotificationCompat.Builder extend(@NonNull NotificationCompat.Builder builder) {

        if (UAStringUtil.isEmpty(arguments.getMessage().getPublicNotificationPayload())) {
            return builder;
        }

        try {
            JsonMap jsonMap = JsonValue.parseString(arguments.getMessage().getPublicNotificationPayload()).optMap();

            NotificationCompat.Builder publicBuilder = new NotificationCompat.Builder(context, arguments.getNotificationChannelId())
                    .setContentTitle(jsonMap.opt(TITLE_KEY).optString())
                    .setContentText(jsonMap.opt(ALERT_KEY).optString())
                    .setColor(accentColor)
                    .setAutoCancel(true)
                    .setSmallIcon(smallIconId);

            if (largeIconId != 0) {
                publicBuilder.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), largeIconId));
            }

            if (jsonMap.containsKey(SUMMARY_KEY)) {
                publicBuilder.setSubText(jsonMap.opt(SUMMARY_KEY).optString());
            }

            builder.setPublicVersion(publicBuilder.build());
        } catch (JsonException e) {
            Logger.error(e, "Failed to parse public notification.");
        }

        return builder;
    }

}

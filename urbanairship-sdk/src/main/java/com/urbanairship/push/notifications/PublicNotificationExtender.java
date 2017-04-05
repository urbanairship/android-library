/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.notifications;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;

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
    private final PushMessage message;
    private int accentColor;
    private int smallIconId;
    private int largeIconId;

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param message The push message.
     */
    public PublicNotificationExtender(@NonNull  Context context, @NonNull PushMessage message) {
        this.context = context;
        this.message = message;
        this.smallIconId = context.getApplicationInfo().icon;
    }

    /**
     * Sets the accent color.
     *
     * @param accentColor The notification's accent color.
     * @return The PublicNotificationExtender to chain calls.
     */
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
    public PublicNotificationExtender setLargeIcon(@DrawableRes int largeIcon) {
        this.largeIconId = largeIcon;
        return this;
    }

    @Override
    public NotificationCompat.Builder extend(NotificationCompat.Builder builder) {

        if (UAStringUtil.isEmpty(message.getPublicNotificationPayload())) {
            return builder;
        }

        try {
            JsonMap jsonMap = JsonValue.parseString(message.getPublicNotificationPayload()).optMap();

            NotificationCompat.Builder publicBuilder = new NotificationCompat.Builder(context)
                    .setContentTitle(jsonMap.opt(TITLE_KEY).getString(""))
                    .setContentText(jsonMap.opt(ALERT_KEY).getString(""))
                    .setColor(accentColor)
                    .setAutoCancel(true)
                    .setSmallIcon(smallIconId);

            if (largeIconId != 0) {
                publicBuilder.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), largeIconId));
            }

            if (jsonMap.containsKey(SUMMARY_KEY)) {
                publicBuilder.setSubText(jsonMap.opt(SUMMARY_KEY).getString(""));
            }

            builder.setPublicVersion(publicBuilder.build());
        } catch (JsonException e) {
            Logger.error("Failed to parse public notification.", e);
        }

        return builder;
    }
}

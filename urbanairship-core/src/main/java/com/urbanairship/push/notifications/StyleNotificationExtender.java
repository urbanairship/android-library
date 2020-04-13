/* Copyright Airship and Contributors */

package com.urbanairship.push.notifications;

import android.content.Context;
import android.graphics.Bitmap;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.UAStringUtil;

import java.net.MalformedURLException;
import java.net.URL;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * Notification builder extender to add the public notification defined by a {@link PushMessage}.
 */
public class StyleNotificationExtender implements NotificationCompat.Extender {

    // Notification styles
    static final String TITLE_KEY = "title";
    static final String SUMMARY_KEY = "summary";
    static final String TYPE_KEY = "type";
    static final String BIG_TEXT_KEY = "big_text";
    static final String BIG_PICTURE_KEY = "big_picture";
    static final String INBOX_KEY = "inbox";
    static final String LINES_KEY = "lines";

    private final PushMessage message;
    private final Context context;
    private NotificationCompat.Style defaultStyle;

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param message The push message.
     */
    public StyleNotificationExtender(@NonNull Context context, @NonNull PushMessage message) {
        this.context = context.getApplicationContext();
        this.message = message;
    }

    /**
     * Sets the default style if {@link PushMessage} does not define a style, or it fails to
     * create the style.
     *
     * @param defaultStyle The default style.
     * @return The StyleNotificationExtender to chain calls.
     */
    @NonNull
    public StyleNotificationExtender setDefaultStyle(@Nullable NotificationCompat.Style defaultStyle) {
        this.defaultStyle = defaultStyle;
        return this;
    }

    @NonNull
    @Override
    public NotificationCompat.Builder extend(@NonNull NotificationCompat.Builder builder) {
        if (!applyStyle(builder) && defaultStyle != null) {
            builder.setStyle(defaultStyle);
        }

        return builder;
    }

    /**
     * Applies the notification style.
     *
     * @param builder The notification builder.
     * @return {@code true} if the style was applied, otherwise {@code false}.
     */
    private boolean applyStyle(@NonNull NotificationCompat.Builder builder) {
        String stylePayload = message.getStylePayload();
        if (stylePayload == null) {
            return false;
        }

        JsonMap styleJson;
        try {
            styleJson = JsonValue.parseString(stylePayload).optMap();
        } catch (JsonException e) {
            Logger.error(e, "Failed to parse notification style payload.");
            return false;
        }

        String type = styleJson.opt(TYPE_KEY).optString();

        switch (type) {
            case BIG_TEXT_KEY:
                applyBigTextStyle(builder, styleJson);
                return true;

            case INBOX_KEY:
                applyInboxStyle(builder, styleJson);
                return true;

            case BIG_PICTURE_KEY:
                return applyBigPictureStyle(builder, styleJson);

            default:
                Logger.error("Unrecognized notification style type: %s", type);
                return false;
        }
    }

    /**
     * Applies the big text notification style.
     *
     * @param builder The notification builder.
     * @param styleJson The JsonMap style.
     * @return {@code true} if the style was applied, otherwise {@code false}.
     */
    private boolean applyBigTextStyle(@NonNull NotificationCompat.Builder builder, @NonNull JsonMap styleJson) {
        NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle();

        String title = styleJson.opt(TITLE_KEY).getString();
        String summary = styleJson.opt(SUMMARY_KEY).getString();

        String bigText = styleJson.opt(BIG_TEXT_KEY).getString();
        if (!UAStringUtil.isEmpty(bigText)) {
            style.bigText(bigText);
        }

        if (!UAStringUtil.isEmpty(title)) {
            style.setBigContentTitle(title);
        }

        if (!UAStringUtil.isEmpty(summary)) {
            style.setSummaryText(summary);
        }

        builder.setStyle(style);
        return true;
    }

    /**
     * Applies the big picture notification style.
     *
     * @param builder The notification builder.
     * @param styleJson The JsonMap style.
     * @return {@code true} if the style was applied, otherwise {@code false}.
     */
    private boolean applyBigPictureStyle(@NonNull NotificationCompat.Builder builder, @NonNull JsonMap styleJson) {
        NotificationCompat.BigPictureStyle style = new NotificationCompat.BigPictureStyle();

        String title = styleJson.opt(TITLE_KEY).getString();
        String summary = styleJson.opt(SUMMARY_KEY).getString();

        URL url;

        try {
            url = new URL(styleJson.opt(BIG_PICTURE_KEY).optString());
        } catch (MalformedURLException e) {
            Logger.error(e, "Malformed big picture URL.");
            return false;
        }

        Bitmap bitmap = NotificationUtils.fetchBigImage(context, url);

        if (bitmap == null) {
            return false;
        }

        // Set big picture image
        style.bigPicture(bitmap);

        // Clear the large icon when the big picture is expanded
        style.bigLargeIcon(null);

        // Set the image as the large icon to show the image when collapsed
        builder.setLargeIcon(bitmap);

        if (!UAStringUtil.isEmpty(title)) {
            style.setBigContentTitle(title);
        }

        if (!UAStringUtil.isEmpty(summary)) {
            style.setSummaryText(summary);
        }

        builder.setStyle(style);
        return true;
    }

    /**
     * Applies the inbox notification style.
     *
     * @param builder The notification builder.
     * @param styleJson The JsonMap style.
     */
    private void applyInboxStyle(@NonNull NotificationCompat.Builder builder, @NonNull JsonMap styleJson) {
        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();

        String title = styleJson.opt(TITLE_KEY).getString();
        String summary = styleJson.opt(SUMMARY_KEY).getString();

        JsonList lines = styleJson.opt(LINES_KEY).optList();
        for (JsonValue line : lines) {
            String lineValue = line.getString();
            if (!UAStringUtil.isEmpty(lineValue)) {
                style.addLine(lineValue);
            }
        }

        if (!UAStringUtil.isEmpty(title)) {
            style.setBigContentTitle(title);
        }

        if (!UAStringUtil.isEmpty(summary)) {
            style.setSummaryText(summary);
        }

        builder.setStyle(style);
    }



}

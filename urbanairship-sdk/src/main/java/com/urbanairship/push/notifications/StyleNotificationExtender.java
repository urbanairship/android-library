/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push.notifications;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.WindowManager;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.BitmapUtils;
import com.urbanairship.util.UAStringUtil;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Notification builder extender to add the public notification defined by a {@link PushMessage}.
 */
public class StyleNotificationExtender implements NotificationCompat.Extender {

    private final static int BIG_IMAGE_HEIGHT_DP = 240;
    private final static double BIG_IMAGE_SCREEN_WIDTH_PERCENT = .75;

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
    public StyleNotificationExtender(Context context, PushMessage message) {
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
    public StyleNotificationExtender setDefaultStyle(NotificationCompat.Style defaultStyle) {
        this.defaultStyle = defaultStyle;
        return this;
    }

    @Override
    public NotificationCompat.Builder extend(NotificationCompat.Builder builder) {
        NotificationCompat.Style style = createStyle();

        if (style != null) {
            builder.setStyle(style);
        } else if (defaultStyle != null) {
            builder.setStyle(defaultStyle);
        }

        return builder;
    }

    @Nullable
    private NotificationCompat.Style createStyle() {
        String stylePayload = message.getStylePayload();
        if (stylePayload == null) {
            return null;
        }

        JsonMap styleJson;
        try {
            styleJson = JsonValue.parseString(stylePayload).optMap();
        } catch (JsonException e) {
            Logger.error("Failed to parse notification style payload.", e);
            return null;
        }

        String type = styleJson.opt(TYPE_KEY).getString("");

        NotificationCompat.Style style = null;
        switch (type) {
            case BIG_TEXT_KEY:
                style = createBigTextStyle(styleJson);
                break;
            case INBOX_KEY:
                style = createInboxStyle(styleJson);
                break;
            case BIG_PICTURE_KEY:
                style = createBigPictureStyle(styleJson);
                break;
        }

        return style;
    }

    /**
     * Creates the big text notification style.
     *
     * @param styleJson The JsonMap style.
     * @return The big text style.
     */
    private NotificationCompat.Style createBigTextStyle(@NonNull JsonMap styleJson) {
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

        return style;
    }

    /**
     * Creates the big picture notification style.
     *
     * @param styleJson The JsonMap style.
     * @return The big picture style or null if it failed to be created.
     */
    private NotificationCompat.BigPictureStyle createBigPictureStyle(@NonNull JsonMap styleJson) {
        NotificationCompat.BigPictureStyle style = new NotificationCompat.BigPictureStyle();

        String title = styleJson.opt(TITLE_KEY).getString();
        String summary = styleJson.opt(SUMMARY_KEY).getString();

        try {
            URL url = new URL(styleJson.opt(BIG_PICTURE_KEY).getString(""));
            Bitmap bitmap;
            try {
                bitmap = fetchBigImage(url);
            } catch (IOException e) {
                Logger.error("Failed to create big picture style, unable to fetch image: " + e);
                return null;
            }
            if (bitmap == null) {
                Logger.error("Failed to create big picture style, unable to fetch image: " + url);
                return null;
            }
            style.bigPicture(bitmap);
        } catch (MalformedURLException e) {
            Logger.error("Malformed big picture URL.", e);
            return null;
        }

        if (!UAStringUtil.isEmpty(title)) {
            style.setBigContentTitle(title);
        }

        if (!UAStringUtil.isEmpty(summary)) {
            style.setSummaryText(summary);
        }

        return style;
    }

    /**
     * Creates the inbox notification style.
     *
     * @param styleJson The JsonMap style.
     * @return The inbox style.
     */
    private NotificationCompat.InboxStyle createInboxStyle(@NonNull JsonMap styleJson) {
        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();

        String title = styleJson.opt(TITLE_KEY).getString();
        String summary = styleJson.opt(SUMMARY_KEY).getString();

        JsonList lines = styleJson.opt(LINES_KEY).optList();
        for (JsonValue line : lines) {
            String lineValue = line.getString();
            if (UAStringUtil.isEmpty(lineValue)) {
                style.addLine(lineValue);
            }
        }

        if (!UAStringUtil.isEmpty(title)) {
            style.setBigContentTitle(title);
        }

        if (!UAStringUtil.isEmpty(summary)) {
            style.setSummaryText(summary);
        }

        return style;
    }

    /**
     * Fetches a big image for a given URL. Attempts to sample the image down to a reasonable size
     * before loading into memory.
     *
     * @param url The image URL.
     * @return The bitmap, or null if it failed to be fetched.
     * @throws IOException
     */
    @Nullable
    private Bitmap fetchBigImage(@Nullable URL url) throws IOException {
        if (url == null) {
            return null;
        }

        Logger.info("Fetching notification image at URL: " + url);
        WindowManager window = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        window.getDefaultDisplay().getMetrics(dm);

        // Since notifications do not take up the entire screen, request 3/4 the longest device dimension
        int reqWidth = (int) (Math.max(dm.widthPixels, dm.heightPixels) * BIG_IMAGE_SCREEN_WIDTH_PERCENT);

        // Big images have a max height of 240dp
        int reqHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, BIG_IMAGE_HEIGHT_DP, dm);

        return BitmapUtils.fetchScaledBitmap(context, url, reqWidth, reqHeight);
    }
}

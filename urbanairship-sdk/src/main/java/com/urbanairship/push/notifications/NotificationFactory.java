/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push.notifications;

import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.WindowManager;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
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
import java.util.ArrayList;
import java.util.List;


/**
 * This abstract class provides a pathway for customizing the display of push notifications
 * in the Android <code>NotificationManager</code>.
 *
 * @see SystemNotificationFactory
 * @see DefaultNotificationFactory
 * @see CustomLayoutNotificationFactory
 */
public abstract class NotificationFactory {

    /**
     * Notification Factory default flags.
     */
    static final int NOTIFICATION_DEFAULTS = NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_VIBRATE;

    private final static int BIG_IMAGE_HEIGHT_DP = 240;
    private final static double BIG_IMAGE_SCREEN_WIDTH_PERCENT = .75;

    private final Context context;

    public NotificationFactory(Context context) {
        this.context = context.getApplicationContext();
    }

    public Context getContext() {
        return context;
    }

    static final String TITLE_KEY = "title";
    static final String SUMMARY_KEY = "summary";
    static final String ALERT_KEY = "alert";

    // Notification styles
    static final String TYPE_KEY = "type";
    static final String BIG_TEXT_KEY = "big_text";
    static final String BIG_PICTURE_KEY = "big_picture";
    static final String INBOX_KEY = "inbox";
    static final String LINES_KEY = "lines";

    // Wearable
    static final String INTERACTIVE_TYPE_KEY = "interactive_type";
    static final String INTERACTIVE_ACTIONS_KEY = "interactive_actions";
    static final String BACKGROUND_IMAGE_KEY = "background_image";
    static final String EXTRA_PAGES_KEY = "extra_pages";

    /**
     * Creates a <code>Notification</code> for an incoming push message.
     * <p/>
     * In order to handle notification opens, the application should register a broadcast receiver
     * that extends {@link com.urbanairship.push.BaseIntentReceiver}. When the notification is opened
     * it will call {@link com.urbanairship.push.BaseIntentReceiver#onNotificationOpened(Context, PushMessage, int)}
     * giving the application a chance to handle the notification open. If the broadcast receiver is not registered,
     * or {@code false} is returned, an open will be handled by either starting the launcher activity or
     * by sending the notification's content intent if it is present.
     *
     * @param message The push message.
     * @param notificationId The notification ID.
     * @return The notification to display, or <code>null</code> if no notification is desired.
     */
    @Nullable
    public abstract Notification createNotification(@NonNull PushMessage message, int notificationId);

    /**
     * Creates a notification ID based on the message and payload.
     * <p/>
     * This method could return a constant (to always replace the existing ID)
     * or a payload/message specific ID (to replace in cases where there are duplicates, for example)
     * or a random/sequential (to always add a new notification).
     *
     * @param pushMessage The push message.
     * @return An integer ID for the next notification.
     */
    public abstract int getNextId(@NonNull PushMessage pushMessage);

    /**
     * Creates a notification extender with actions applied.
     * @param message The push message.
     * @param notificationId The notification ID.
     * @return The notification extender.
     */
    protected final NotificationCompat.Extender createNotificationActionsExtender(@NonNull PushMessage message, int notificationId) {
        NotificationActionButtonGroup actionGroup = UAirship.shared().getPushManager().getNotificationActionGroup(message.getInteractiveNotificationType());

        final List<NotificationCompat.Action> androidActions = new ArrayList<>();

        if (actionGroup != null) {
            androidActions.addAll(actionGroup.createAndroidActions(getContext(), message, notificationId, message.getInteractiveActionsPayload()));
        }

        return new NotificationCompat.Extender() {
            @Override
            public NotificationCompat.Builder extend(NotificationCompat.Builder builder) {

                for (NotificationCompat.Action action : androidActions) {
                    builder.addAction(action);
                }

                return builder;
            }
        };
    }

    /**
     * Creates a notification extender with wearable extensions.
     * @param message The push message.
     * @param notificationId The notification ID.
     * @return The wearable notification extender.
     * @throws IOException
     */
    protected final NotificationCompat.WearableExtender createWearableExtender(@NonNull PushMessage message, int notificationId) throws IOException {
        NotificationCompat.WearableExtender extender = new NotificationCompat.WearableExtender();

        String wearablePayload = message.getWearablePayload();
        if (wearablePayload == null) {
            return extender;
        }

        JsonMap wearableJson;
        try {
            wearableJson = JsonValue.parseString(wearablePayload).optMap();
        } catch (JsonException e) {
            Logger.error("Failed to parse wearable payload.", e);
            return extender;
        }

        String actionGroupId = wearableJson.opt(INTERACTIVE_TYPE_KEY).getString();
        String actionsPayload = wearableJson.opt(INTERACTIVE_ACTIONS_KEY).toString();
        if (UAStringUtil.isEmpty(actionsPayload)) {
            actionsPayload = message.getInteractiveActionsPayload();
        }

        if (!UAStringUtil.isEmpty(actionGroupId)) {
            NotificationActionButtonGroup actionGroup = UAirship.shared().getPushManager().getNotificationActionGroup(actionGroupId);

            if (actionGroup != null) {
                List<NotificationCompat.Action> androidActions = actionGroup.createAndroidActions(getContext(), message, notificationId, actionsPayload);
                extender.addActions(androidActions);
            }
        }

        String backgroundUrl = wearableJson.opt(BACKGROUND_IMAGE_KEY).getString();
        if (!UAStringUtil.isEmpty(backgroundUrl)) {
            try {
                Bitmap bitmap = fetchBigImage(new URL(backgroundUrl));
                extender.setBackground(bitmap);
            } catch (MalformedURLException e) {
                Logger.error("Wearable background url is malformed.", e);
            }
        }

        JsonList pages = wearableJson.opt(EXTRA_PAGES_KEY).optList();
        for (JsonValue page : pages) {
            if (!page.isJsonMap()) {
                continue;
            }
            extender.addPage(createWearPage(page.optMap()));
        }

        return extender;
    }

    /**
     * Creates a notification style.
     * @param message The push message.
     * @return The notification style or null if it failed to be created.
     * @throws IOException
     */
    protected final NotificationCompat.Style createNotificationStyle(@NonNull PushMessage message) throws IOException {
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

        switch (type) {
            case BIG_TEXT_KEY:
                return createBigTextStyle(styleJson);
            case INBOX_KEY:
                return createInboxStyle(styleJson);
            case BIG_PICTURE_KEY:
                return createBigPictureStyle(styleJson);
        }

        return null;
    }

    /**
     * Creates the pages of the wearable notification.
     * @param page The JsonMap page.
     * @return The notification with pages.
     */
    private Notification createWearPage(@NonNull JsonMap page) {
        NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle();

        String title = page.opt(TITLE_KEY).getString();
        if (!UAStringUtil.isEmpty(title)) {
            style.setBigContentTitle(title);
        }

        String alert = page.opt(ALERT_KEY).getString();
        if (!UAStringUtil.isEmpty(alert)) {
            style.bigText(alert);
        }

        return new NotificationCompat.Builder(context)
                .setAutoCancel(true)
                .setStyle(style)
                .build();
    }

    /**
     * Creates the big text notification style.
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
     * @param styleJson The JsonMap style.
     * @return The big picture style or null if it failed to be created.
     * @throws IOException
     */
    private NotificationCompat.BigPictureStyle createBigPictureStyle(@NonNull JsonMap styleJson) throws IOException {
        NotificationCompat.BigPictureStyle style = new NotificationCompat.BigPictureStyle();

        String title = styleJson.opt(TITLE_KEY).getString();
        String summary = styleJson.opt(SUMMARY_KEY).getString();

        try {
            URL url = new URL(styleJson.opt(BIG_PICTURE_KEY).getString(""));
            Bitmap bitmap = fetchBigImage(url);
            if (bitmap == null) {
                Logger.error("Failed to create big picture style, unable to fetch image: " + url);
                return null;
            }
            style.bigPicture(fetchBigImage(url));
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
     * Creates the public version notification.
     * @param message The push message.
     * @param notificationIcon The notification icon.
     * @return The public version notification or null if it failed to be created.
     */
    protected final Notification createPublicVersionNotification(@NonNull PushMessage message, int notificationIcon) {

        if (!UAStringUtil.isEmpty(message.getPublicNotificationPayload())) {
            try {
                JsonMap jsonMap = JsonValue.parseString(message.getPublicNotificationPayload()).optMap();

                NotificationCompat.Builder publicBuilder = new NotificationCompat.Builder(getContext())
                        .setContentTitle(jsonMap.opt(TITLE_KEY).getString(""))
                        .setContentText(jsonMap.opt(ALERT_KEY).getString(""))
                        .setAutoCancel(true)
                        .setSmallIcon(notificationIcon);

                if (jsonMap.containsKey(SUMMARY_KEY)) {
                    publicBuilder.setSubText(jsonMap.opt(SUMMARY_KEY).getString(""));
                }
                return publicBuilder.build();
            } catch (JsonException e) {
                Logger.error("Failed to parse public notification.", e);
            }
        }

        return null;
    }


    /**
     * Fetches a big image for a given URL. Attempts to sample the image down to a reasonable size
     * before loading into memory.
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

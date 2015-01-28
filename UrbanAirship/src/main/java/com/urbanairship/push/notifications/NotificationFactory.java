/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.urbanairship.push.notifications;

import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.app.NotificationCompat;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.WindowManager;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.BitmapUtils;
import com.urbanairship.util.UAStringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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

    private Context context;

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
     * Creates a <code>Notification</code> for use in the notification bar,
     * optionally triggering vibration and sound based on the user's preferences.
     * <p/>
     * If no notification should be displayed for this payload, return <code>null</code>.
     *
     * @param message The push message.
     * @param notificationId The notification ID.
     * @return The notification to display, or <code>null</code> if no notification is desired.
     */
    public abstract Notification createNotification(PushMessage message, int notificationId);

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
    public abstract int getNextId(PushMessage pushMessage);

    /**
     * Creates a notification extender with actions applied.
     * @param message The push message.
     * @param notificationId The notification ID.
     * @return The notification extender.
     */
    protected final NotificationCompat.Extender createNotificationActionsExtender(PushMessage message, int notificationId) {
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
    protected final NotificationCompat.WearableExtender createWearableExtender(PushMessage message, int notificationId) throws IOException {
        NotificationCompat.WearableExtender extender = new NotificationCompat.WearableExtender();

        String wearablePayload = message.getWearablePayload();
        if (wearablePayload == null) {
            return extender;
        }

        JSONObject wearableJSON;
        try {
            wearableJSON = new JSONObject(wearablePayload);
        } catch (JSONException e) {
            Logger.error("Failed to parse wearable payload.", e);
            return extender;
        }

        String actionGroupId = wearableJSON.optString(INTERACTIVE_TYPE_KEY);
        String actionsPayload = wearableJSON.optString(INTERACTIVE_ACTIONS_KEY, message.getInteractiveActionsPayload());
        if (!UAStringUtil.isEmpty(actionGroupId)) {
            NotificationActionButtonGroup actionGroup = UAirship.shared().getPushManager().getNotificationActionGroup(actionGroupId);

            if (actionGroup != null) {
                List<NotificationCompat.Action> androidActions = actionGroup.createAndroidActions(getContext(), message, notificationId, actionsPayload);
                extender.addActions(androidActions);
            }
        }

        String backgroundUrl = wearableJSON.optString(BACKGROUND_IMAGE_KEY);
        if (!UAStringUtil.isEmpty(backgroundUrl)) {
            try {
                Bitmap bitmap = fetchBigImage(new URL(backgroundUrl));
                extender.setBackground(bitmap);
            } catch (MalformedURLException e) {
                Logger.error("Wearable background url is malformed.", e);
            }
        }

        JSONArray pages = wearableJSON.optJSONArray(EXTRA_PAGES_KEY);
        if (pages != null) {
            for (int i = 0; i < pages.length(); i++) {
                JSONObject page = pages.optJSONObject(i);
                if (page == null) {
                    continue;
                }

                extender.addPage(createWearPage(page));
            }
        }

        return extender;
    }

    /**
     * Creates a notification style.
     * @param message The push message.
     * @return The notification style or null if it failed to be created.
     * @throws IOException
     */
    protected final NotificationCompat.Style createNotificationStyle(PushMessage message) throws IOException {
        String stylePayload = message.getStylePayload();
        if (stylePayload == null) {
            return null;
        }

        JSONObject styleJSON;
        try {
            styleJSON = new JSONObject(stylePayload);
        } catch (JSONException e) {
            Logger.error("Failed to parse notification style payload.", e);
            return null;
        }

        String type = styleJSON.optString(TYPE_KEY);

        switch (type) {
            case BIG_TEXT_KEY:
                return createBigTextStyle(styleJSON);
            case INBOX_KEY:
                return createInboxStyle(styleJSON);
            case BIG_PICTURE_KEY:
                return createBigPictureStyle(styleJSON);
        }

        return null;
    }

    /**
     * Creates the pages of the wearable notification.
     * @param page The JSONObject page.
     * @return The notification with pages.
     */
    private Notification createWearPage(JSONObject page) {
        NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle();

        String title = page.optString(TITLE_KEY);
        if (!UAStringUtil.isEmpty(title)) {
            style.setBigContentTitle(title);
        }

        String alert = page.optString(ALERT_KEY);
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
     * @param styleJSON The JSONObject style.
     * @return The big text style.
     */
    private NotificationCompat.Style createBigTextStyle(JSONObject styleJSON) {
        NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle();

        String title = styleJSON.optString(TITLE_KEY);
        String summary = styleJSON.optString(SUMMARY_KEY);

        String bigText = styleJSON.optString(BIG_TEXT_KEY);
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
     * @param styleJSON The JSONObject style.
     * @return The big picture style or null if it failed to be created.
     * @throws IOException
     */
    private NotificationCompat.BigPictureStyle createBigPictureStyle(JSONObject styleJSON) throws IOException {
        NotificationCompat.BigPictureStyle style = new NotificationCompat.BigPictureStyle();

        String title = styleJSON.optString(TITLE_KEY);
        String summary = styleJSON.optString(SUMMARY_KEY);

        try {
            URL url = new URL(styleJSON.optString(BIG_PICTURE_KEY));
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
     * @param styleJSON The JSONObject style.
     * @return The inbox style.
     */
    private NotificationCompat.InboxStyle createInboxStyle(JSONObject styleJSON) {
        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();

        String title = styleJSON.optString(TITLE_KEY);
        String summary = styleJSON.optString(SUMMARY_KEY);

        JSONArray lines = styleJSON.optJSONArray(LINES_KEY);
        if (lines != null) {
            for (int i = 0; i < lines.length(); i++) {
                String line = lines.optString(i);
                if (line != null) {
                    style.addLine(line);
                }
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
    protected final Notification createPublicVersionNotification(PushMessage message, int notificationIcon) {

        if (!UAStringUtil.isEmpty(message.getPublicNotificationPayload())) {
            try {
                JSONObject jsonObject = new JSONObject(message.getPublicNotificationPayload());

                NotificationCompat.Builder publicBuilder = new NotificationCompat.Builder(getContext())
                        .setContentTitle(jsonObject.optString(TITLE_KEY))
                        .setContentText(jsonObject.optString(ALERT_KEY))
                        .setAutoCancel(true)
                        .setSmallIcon(notificationIcon);

                if (jsonObject.has(SUMMARY_KEY)) {
                    publicBuilder.setSubText(jsonObject.optString(SUMMARY_KEY));
                }
                return publicBuilder.build();
            } catch (JSONException e) {
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
    private Bitmap fetchBigImage(URL url) throws IOException {
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

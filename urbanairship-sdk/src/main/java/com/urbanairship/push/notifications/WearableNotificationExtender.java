/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.notifications;

import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;

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
import java.net.URL;
import java.util.List;

/**
 * Notification builder extender to add the wearable overrides defined by a {@link PushMessage}.
 */
public class WearableNotificationExtender implements NotificationCompat.Extender {

    private static final int BACKGROUND_IMAGE_HEIGHT_PX = 480;
    private static final int BACKGROUND_IMAGE_WIDTH_PX = 480;

    static final String TITLE_KEY = "title";
    static final String ALERT_KEY = "alert";

    // Wearable
    static final String INTERACTIVE_TYPE_KEY = "interactive_type";
    static final String INTERACTIVE_ACTIONS_KEY = "interactive_actions";
    static final String BACKGROUND_IMAGE_KEY = "background_image";
    static final String EXTRA_PAGES_KEY = "extra_pages";

    private final PushMessage message;
    private final Context context;
    private final int notificationId;

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param message The push message.
     * @param notificationId The notification ID.
     */
    public WearableNotificationExtender(@NonNull Context context, @NonNull PushMessage message, int notificationId) {
        this.context = context;
        this.message = message;
        this.notificationId = notificationId;
    }

    @Override
    public NotificationCompat.Builder extend(NotificationCompat.Builder builder) {
        String wearablePayload = message.getWearablePayload();
        if (wearablePayload == null) {
            return builder;
        }

        JsonMap wearableJson;
        try {
            wearableJson = JsonValue.parseString(wearablePayload).optMap();
        } catch (JsonException e) {
            Logger.error("Failed to parse wearable payload.", e);
            return builder;
        }

        NotificationCompat.WearableExtender extender = new NotificationCompat.WearableExtender();

        String actionGroupId = wearableJson.opt(INTERACTIVE_TYPE_KEY).getString();
        String actionsPayload = wearableJson.opt(INTERACTIVE_ACTIONS_KEY).toString();
        if (UAStringUtil.isEmpty(actionsPayload)) {
            actionsPayload = message.getInteractiveActionsPayload();
        }

        if (!UAStringUtil.isEmpty(actionGroupId)) {
            NotificationActionButtonGroup actionGroup = UAirship.shared().getPushManager().getNotificationActionGroup(actionGroupId);

            if (actionGroup != null) {
                List<NotificationCompat.Action> androidActions = actionGroup.createAndroidActions(context, message, notificationId, actionsPayload);
                extender.addActions(androidActions);
            }
        }

        String backgroundUrl = wearableJson.opt(BACKGROUND_IMAGE_KEY).getString();
        if (!UAStringUtil.isEmpty(backgroundUrl)) {
            try {
                Bitmap bitmap = BitmapUtils.fetchScaledBitmap(context, new URL(backgroundUrl), BACKGROUND_IMAGE_WIDTH_PX, BACKGROUND_IMAGE_HEIGHT_PX);
                if (bitmap != null) {
                    extender.setBackground(bitmap);
                }
            } catch (IOException e) {
                Logger.error("Unable to fetch background image: ", e);
            }
        }

        JsonList pages = wearableJson.opt(EXTRA_PAGES_KEY).optList();
        for (JsonValue page : pages) {
            if (!page.isJsonMap()) {
                continue;
            }
            extender.addPage(createWearPage(page.optMap()));
        }

        builder.extend(extender);

        return builder;
    }

    /**
     * Creates the pages of the wearable notification.
     *
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
}

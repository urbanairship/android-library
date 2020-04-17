/* Copyright Airship and Contributors */

package com.urbanairship.push.notifications;

import android.app.Notification;
import android.content.Context;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.UAStringUtil;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

/**
 * Notification builder extender to add the wearable overrides defined by a {@link PushMessage}.
 */
public class WearableNotificationExtender implements NotificationCompat.Extender {

    static final String TITLE_KEY = "title";
    static final String ALERT_KEY = "alert";

    // Wearable
    static final String INTERACTIVE_TYPE_KEY = "interactive_type";
    static final String INTERACTIVE_ACTIONS_KEY = "interactive_actions";

    private final Context context;
    private final NotificationArguments arguments;

    /**
     * WearableNotificationExtender default constructor.
     *
     * @param context The application context.
     * @param arguments The notification arguments.
     */
    public WearableNotificationExtender(@NonNull Context context, @NonNull NotificationArguments arguments) {
        this.context = context.getApplicationContext();
        this.arguments = arguments;
    }

    @NonNull
    @Override
    public NotificationCompat.Builder extend(@NonNull NotificationCompat.Builder builder) {
        String wearablePayload = arguments.getMessage().getWearablePayload();
        if (wearablePayload == null) {
            return builder;
        }

        JsonMap wearableJson;
        try {
            wearableJson = JsonValue.parseString(wearablePayload).optMap();
        } catch (JsonException e) {
            Logger.error(e, "Failed to parse wearable payload.");
            return builder;
        }

        NotificationCompat.WearableExtender extender = new NotificationCompat.WearableExtender();

        String actionGroupId = wearableJson.opt(INTERACTIVE_TYPE_KEY).getString();
        String actionsPayload = wearableJson.opt(INTERACTIVE_ACTIONS_KEY).toString();
        if (UAStringUtil.isEmpty(actionsPayload)) {
            actionsPayload = arguments.getMessage().getInteractiveActionsPayload();
        }

        if (!UAStringUtil.isEmpty(actionGroupId)) {
            NotificationActionButtonGroup actionGroup = UAirship.shared().getPushManager().getNotificationActionGroup(actionGroupId);

            if (actionGroup != null) {
                List<NotificationCompat.Action> androidActions = actionGroup.createAndroidActions(context, arguments, actionsPayload);
                extender.addActions(androidActions);
            }
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

        return new NotificationCompat.Builder(context, arguments.getNotificationChannelId())
                .setAutoCancel(true)
                .setStyle(style)
                .build();
    }

}

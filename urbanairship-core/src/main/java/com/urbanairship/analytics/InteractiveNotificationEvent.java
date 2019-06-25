/* Copyright Airship and Contributors */

package com.urbanairship.analytics;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.urbanairship.json.JsonMap;
import com.urbanairship.push.NotificationActionButtonInfo;
import com.urbanairship.push.NotificationInfo;

/**
 * An event that captures information regarding an interactive notification action open.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class InteractiveNotificationEvent extends Event {

    private static final String SEND_ID_KEY = "send_id";

    private static final String BUTTON_GROUP_KEY = "button_group";

    private static final String BUTTON_ID_KEY = "button_id";

    private static final String BUTTON_DESCRIPTION_KEY = "button_description";

    private static final String FOREGROUND_KEY = "foreground";

    private static final String TYPE = "interactive_notification_action";

    private static final String USER_INPUT = "user_input";

    private final String sendId;
    private final String buttonGroupId;
    private final String buttonId;
    private final String buttonDescription;
    private final boolean isForeground;
    private final Bundle remoteInput;

    /**
     * Creates an interactive notification event.
     *
     * @param notificationInfo The notification info.
     * @param buttonInfo The action button info.
     */
    public InteractiveNotificationEvent(NotificationInfo notificationInfo, NotificationActionButtonInfo buttonInfo) {
        this.sendId = notificationInfo.getMessage().getSendId();
        this.buttonGroupId = notificationInfo.getMessage().getInteractiveNotificationType();
        this.buttonId = buttonInfo.getButtonId();
        this.buttonDescription = buttonInfo.getDescription();
        this.isForeground = buttonInfo.isForeground();
        this.remoteInput = buttonInfo.getRemoteInput();
    }

    @NonNull
    @Override
    public final String getType() {
        return TYPE;
    }

    @NonNull
    @Override
    protected final JsonMap getEventData() {
        JsonMap.Builder builder = JsonMap.newBuilder()
                                         .put(SEND_ID_KEY, sendId)
                                         .put(BUTTON_GROUP_KEY, buttonGroupId)
                                         .put(BUTTON_ID_KEY, buttonId)
                                         .put(BUTTON_DESCRIPTION_KEY, buttonDescription)
                                         .put(FOREGROUND_KEY, isForeground);

        if (remoteInput != null && !remoteInput.isEmpty()) {

            JsonMap.Builder remoteInputBuilder = JsonMap.newBuilder();
            for (String key : remoteInput.keySet()) {
                remoteInputBuilder.put(key, remoteInput.getString(key));
            }

            builder.put(USER_INPUT, remoteInputBuilder.build());
        }

        return builder.build();
    }

}

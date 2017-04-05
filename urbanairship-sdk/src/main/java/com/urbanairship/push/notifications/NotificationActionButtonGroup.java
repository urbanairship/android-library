/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.notifications;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Model object encapsulating the data relevant to a notification action button group.
 */
public class NotificationActionButtonGroup {

    private final List<NotificationActionButton> actionButtons;

    private NotificationActionButtonGroup(List<NotificationActionButton> actionButtons) {
        this.actionButtons = new ArrayList<>(actionButtons);
    }

    /**
     * Gets the notification actions.
     *
     * @return A list of notification actions.
     */
    @NonNull
    public List<NotificationActionButton> getNotificationActionButtons() {
        return new ArrayList<>(actionButtons);
    }

    /**
     * Creates a list of Android notification actions.
     *
     * @param context The application context.
     * @param message The incoming push message.
     * @param notificationId The notification ID.
     * @param actionsPayload The actions payload that defines the Urban Airship actions for each
     * interactive notification action.
     * @return List of Android notification actions.
     */
    @NonNull
    List<NotificationCompat.Action> createAndroidActions(Context context, PushMessage message, int notificationId, String actionsPayload) {
        final List<NotificationCompat.Action> androidActions = new ArrayList<>();

        JsonMap notificationActionMap = null;
        if (!UAStringUtil.isEmpty(actionsPayload)) {
            // Run UA actions for the notification action
            try {
                notificationActionMap = JsonValue.parseString(actionsPayload).optMap();
            } catch(JsonException e) {
                Logger.error("Failed to parse notification actions payload: " + actionsPayload, e);
            }
        }

        for (NotificationActionButton action : getNotificationActionButtons()) {
            String actions = notificationActionMap == null ? null : notificationActionMap.opt(action.getId()).toString();
            NotificationCompat.Action androidAction = action.createAndroidNotificationAction(context, actions, message, notificationId);
            androidActions.add(androidAction);
        }

        return androidActions;
    }

    /**
     * Builds the NotificationActionButtonGroup.
     */
    public static class Builder {
        private final List<NotificationActionButton> actionButtons = new ArrayList<>();

        /**
         * Adds a notification action button.
         *
         * @param action The notification action button to add.
         * @return The builder to allow method chaining.
         */
        @NonNull
        public Builder addNotificationActionButton(NotificationActionButton action) {
            actionButtons.add(action);
            return this;
        }

        /**
         * Builds and returns the NotificationActionButtonGroup.
         *
         * @return The NotificationActionButtonGroup.
         */
        @NonNull
        public NotificationActionButtonGroup build() {
            return new NotificationActionButtonGroup(actionButtons);
        }
    }
}

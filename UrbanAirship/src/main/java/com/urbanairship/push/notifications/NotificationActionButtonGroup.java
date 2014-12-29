/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

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

import android.content.Context;
import android.support.v4.app.NotificationCompat;

import com.urbanairship.Logger;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.UAStringUtil;

import org.json.JSONException;
import org.json.JSONObject;

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
    List<NotificationCompat.Action> createAndroidActions(Context context, PushMessage message, int notificationId, String actionsPayload) {
        final List<NotificationCompat.Action> androidActions = new ArrayList<>();

        JSONObject notificationActionJSON = null;
        if (!UAStringUtil.isEmpty(actionsPayload)) {
            // Run UA actions for the notification action
            try {
                notificationActionJSON = new JSONObject(actionsPayload);
            } catch (JSONException e) {
                Logger.error("Failed to parse notification actions payload: " + actionsPayload, e);
            }
        }

        for (NotificationActionButton action : getNotificationActionButtons()) {
            String actions = notificationActionJSON == null ? null : notificationActionJSON.optString(action.getId());
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
        public Builder addNotificationActionButton(NotificationActionButton action) {
            actionButtons.add(action);
            return this;
        }

        /**
         * Builds and returns the NotificationActionButtonGroup.
         *
         * @return The NotificationActionButtonGroup.
         */
        public NotificationActionButtonGroup build() {
            return new NotificationActionButtonGroup(actionButtons);
        }
    }
}

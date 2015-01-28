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

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;

import com.urbanairship.CoreActivity;
import com.urbanairship.CoreReceiver;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.PushMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Model object encapsulating the data relevant to a notification action button.
 */
public class NotificationActionButton {

    private final Bundle extras;
    private final String id;
    private final int labelId;
    private final boolean isForegroundAction;
    private final int iconId;
    private final String description;
    private List<LocalizableRemoteInput> remoteInputs;

    private NotificationActionButton(String id, int iconId, int labelId, String description, Bundle extras, boolean isForegroundAction, List<LocalizableRemoteInput> remoteInputs) {
        this.id = id;
        this.labelId = labelId;
        this.iconId = iconId;
        this.extras = extras;
        this.description = description;
        this.isForegroundAction = isForegroundAction;
        this.remoteInputs = remoteInputs;
    }

    /**
     * Gets the notification ID.
     *
     * @return The notification ID as a string.
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the notification label ID.
     *
     * @return The notification label ID as an int.
     */
    public int getLabel() {
        return labelId;
    }

    /**
     * Gets the notification icon.
     *
     * @return The notification icon as an int.
     */
    public int getIcon() {
        return iconId;
    }

    /**
     * Flag that indicates if it is a foreground action.
     *
     * @return <code>true</code> if it is a foreground action, otherwise <code>false</code>.
     */
    public boolean isForegroundAction() {
        return isForegroundAction;
    }

    /**
     * Gets the notification action extras.
     *
     * @return The extras as a Bundle.
     */
    public Bundle getExtras() {
        return new Bundle(extras);
    }

    /**
     * Gets the remote inputs.
     *
     * @return A list of remote inputs.
     */
    public List<LocalizableRemoteInput> getRemoteInputs() {
        if (remoteInputs == null) {
            return null;
        }

        return new ArrayList<>(remoteInputs);
    }

    /**
     * Creates the notification action.
     *
     * @param context The application context.
     * @param actionsPayload The actions payload for the interactive buttons.
     * @param message The PushMessage.
     * @param notificationId The notification ID.
     * @return The action as a NotificationCompat.Action
     */
    NotificationCompat.Action createAndroidNotificationAction(Context context, String actionsPayload, PushMessage message, int notificationId) {
        String label = labelId > 0 ? context.getString(labelId) : null;
        String actionDescription = description == null ? label : description;

        PendingIntent actionPendingIntent;

        Intent intent = new Intent(PushManager.ACTION_NOTIFICATION_BUTTON_OPENED_PROXY)
                .addCategory(UUID.randomUUID().toString())
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE, message)
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID, id)
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ACTIONS_PAYLOAD, actionsPayload)
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, isForegroundAction)
                .putExtra(PushManager.EXTRA_NOTIFICATION_ACTION_BUTTON_DESCRIPTION, actionDescription);

        if (isForegroundAction) {
            intent.setClass(context, CoreActivity.class);
            actionPendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
        } else {
            intent.setClass(context, CoreReceiver.class);
            actionPendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
        }

        NotificationCompat.Action.Builder actionBuilder = new NotificationCompat.Action.Builder(iconId, label, actionPendingIntent)
                .addExtras(extras);

        if (remoteInputs != null) {
            for (LocalizableRemoteInput remoteInput : remoteInputs) {
                actionBuilder.addRemoteInput(remoteInput.createRemoteInput(context));
            }
        }

        return actionBuilder.build();
    }

    /**
     * Builds the NotificationAction.
     */
    public static class Builder {
        private String buttonId;
        private int labelId = 0;
        private int iconId = 0;
        private boolean isForegroundAction = true;
        private List<LocalizableRemoteInput> remoteInputs;
        private List<NotificationCompat.Action.Extender> extenders;
        private String description;

        /**
         * Set the buttonId.
         *
         * @param buttonId A string value.
         */
        public Builder(String buttonId) {
            this.buttonId = buttonId;
        }

        /**
         * Set the labelId.
         *
         * @param labelId An int value.
         * @return The builder with the labelId value set.
         */
        public Builder setLabel(int labelId) {
            this.labelId = labelId;
            return this;
        }

        /**
         * Sets the description of the action. Used for analytics.
         * @param description The action description.
         * @return The builder with the description set.
         */
        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        /**
         * Set the icon.
         *
         * @param iconId An int value.
         * @return The builder with the iconId value set.
         */
        public Builder setIcon(int iconId) {
            this.iconId = iconId;
            return this;
        }

        /**
         * Set the isForegroundAction flag. Defaults to true.
         *
         * @param isForegroundAction A boolean value.
         * @return The builder with the isForegroundAction value set.
         */
        public Builder setPerformsInForeground(boolean isForegroundAction) {
            this.isForegroundAction = isForegroundAction;
            return this;
        }

        /**
         * Add a remoteInput.
         *
         * @param remoteInput A LocalizableRemoteInput value.
         * @return The builder with the remoteInput value set.
         */
        public Builder addRemoteInput(LocalizableRemoteInput remoteInput) {
            if (remoteInputs == null) {
                remoteInputs = new ArrayList<>();
            }
            remoteInputs.add(remoteInput);
            return this;
        }

        /**
         * Extends the notification action.
         *
         * @param extender A NotificationCompat.Action.Extender value.
         * @return The builder with the extender value added.
         */
        public Builder extend(NotificationCompat.Action.Extender extender) {
            if (extenders == null) {
                extenders = new ArrayList<>();
            }
            extenders.add(extender);
            return this;
        }

        /**
         * Builds and return the notification action.
         *
         * @return The notification action.
         */
        public NotificationActionButton build() {
            NotificationCompat.Action.Builder builder = new NotificationCompat.Action.Builder(iconId, null, null);
            if (extenders != null) {
                for (NotificationCompat.Action.Extender extender : extenders) {
                    builder.extend(extender);
                }
            }

            NotificationCompat.Action action = builder.build();

            return new NotificationActionButton(buttonId, action.icon, labelId, description, action.getExtras(), isForegroundAction, remoteInputs);
        }
    }
}

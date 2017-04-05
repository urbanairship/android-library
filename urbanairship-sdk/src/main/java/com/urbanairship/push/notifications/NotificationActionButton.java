/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
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
    private final List<LocalizableRemoteInput> remoteInputs;

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
     * Gets the button's description.
     *
     * @return The button's description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the button's ID.
     *
     * @return The button's ID as a string.
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the button's label ID.
     *
     * @return The button's label ID as an int.
     */
    @StringRes
    public int getLabel() {
        return labelId;
    }

    /**
     * Gets the button's icon.
     *
     * @return The button's icon as an int.
     */
    @DrawableRes
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
     * Gets the button's extras.
     *
     * @return The extras as a Bundle.
     */
    @NonNull
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
        String label = labelId > 0 ? context.getString(labelId) : "";
        String actionDescription = description == null ? label : description;

        PendingIntent actionPendingIntent;

        Intent intent = new Intent(PushManager.ACTION_NOTIFICATION_BUTTON_OPENED_PROXY)
                .addCategory(UUID.randomUUID().toString())
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, message.getPushBundle())
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
        private final String buttonId;
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
        @NonNull
        public Builder setLabel(@StringRes int labelId) {
            this.labelId = labelId;
            return this;
        }

        /**
         * Sets the description of the action. Used for analytics.
         *
         * @param description The action description.
         * @return The builder with the description set.
         */
        @NonNull
        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        /**
         * Set the icon.
         * <p/>
         * Note: All notification icons should be entirely white on a transparent background.
         *
         * @param iconId An int value.
         * @return The builder with the iconId value set.
         */
        @NonNull
        public Builder setIcon(@DrawableRes int iconId) {
            this.iconId = iconId;
            return this;
        }

        /**
         * Set the isForegroundAction flag. Defaults to true.
         *
         * @param isForegroundAction A boolean value.
         * @return The builder with the isForegroundAction value set.
         */
        @NonNull
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
        @NonNull
        public Builder addRemoteInput(@NonNull LocalizableRemoteInput remoteInput) {
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
        @NonNull
        public Builder extend(@NonNull NotificationCompat.Action.Extender extender) {
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
        @NonNull
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

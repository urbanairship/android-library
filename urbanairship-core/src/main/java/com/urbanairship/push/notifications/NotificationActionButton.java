/* Copyright Airship and Contributors */

package com.urbanairship.push.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;

import com.urbanairship.push.NotificationProxyActivity;
import com.urbanairship.push.NotificationProxyReceiver;
import com.urbanairship.push.PushManager;

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
    private final String label;
    private final boolean isForegroundAction;
    private final int iconId;
    private final String description;
    private final List<LocalizableRemoteInput> remoteInputs;

    private NotificationActionButton(@NonNull Builder builder, @NonNull Bundle extras) {
        this.id = builder.buttonId;
        this.labelId = builder.labelId;
        this.label = builder.label;
        this.iconId = builder.iconId;
        this.description = builder.description;
        this.isForegroundAction = builder.isForegroundAction;
        this.remoteInputs = builder.remoteInputs;
        this.extras = extras;
    }

    /**
     * Gets the button's description.
     *
     * @return The button's description.
     */
    @Nullable
    public String getDescription() {
        return description;
    }

    /**
     * Gets the button's ID.
     *
     * @return The button's ID as a string.
     */
    @NonNull
    public String getId() {
        return id;
    }

    /**
     * Gets the button's label.
     *
     * @return The button's label.
     */
    @Nullable
    public String getLabel(@NonNull Context context) {
        if (label != null) {
            return label;
        }
        if (labelId != 0) {
            return context.getString(labelId);
        }
        return null;
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
    @Nullable
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
     * @param arguments The notification arguments.
     * @return The action as a NotificationCompat.Action
     */
    @NonNull
    NotificationCompat.Action createAndroidNotificationAction(@NonNull Context context, @Nullable String actionsPayload, @NonNull NotificationArguments arguments) {
        String label = getLabel(context);
        if (label == null) {
            label = "";
        }

        String actionDescription = description == null ? label : description;

        PendingIntent actionPendingIntent;

        Intent intent = new Intent(PushManager.ACTION_NOTIFICATION_RESPONSE)
                .addCategory(UUID.randomUUID().toString())
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, arguments.getMessage().getPushBundle())
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, arguments.getNotificationId())
                .putExtra(PushManager.EXTRA_NOTIFICATION_TAG, arguments.getNotificationTag())
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID, id)
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ACTIONS_PAYLOAD, actionsPayload)
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, isForegroundAction)
                .putExtra(PushManager.EXTRA_NOTIFICATION_ACTION_BUTTON_DESCRIPTION, actionDescription);

        if (isForegroundAction) {
            intent.setClass(context, NotificationProxyActivity.class);
            actionPendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
        } else {
            intent.setClass(context, NotificationProxyReceiver.class);
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
     * Creates a new Builder from a button ID.
     *
     * @param buttonId The button identifier.
     * @return A new builder.
     */
    @NonNull
    public static Builder newBuilder(@NonNull String buttonId) {
        return new Builder(buttonId);
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
        private String label;

        /**
         * Set the buttonId.
         *
         * @param buttonId A string value.
         */
        public Builder(@NonNull String buttonId) {
            this.buttonId = buttonId;
        }

        /**
         * Set the label from a string resource.
         *
         * @param labelId An int value.
         * @return The builder instance.
         */
        @NonNull
        public Builder setLabel(@StringRes int labelId) {
            this.labelId = labelId;
            this.label = null;
            return this;
        }

        /**
         * Set the label.
         *
         * @param label The label.
         * @return The builder instance.
         */
        @NonNull
        public Builder setLabel(@Nullable String label) {
            this.labelId = 0;
            this.label = label;
            return this;
        }

        /**
         * Sets the description of the action. Used for analytics.
         *
         * @param description The action description.
         * @return The builder with the description set.
         */
        @NonNull
        public Builder setDescription(@Nullable String description) {
            this.description = description;
            return this;
        }

        /**
         * Set the icon.
         * <p>
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
            Bundle extras;
            if (extenders != null) {
                NotificationCompat.Action.Builder builder = new NotificationCompat.Action.Builder(iconId, null, null);

                for (NotificationCompat.Action.Extender extender : extenders) {
                    builder.extend(extender);
                }
                extras = builder.build().getExtras();
            } else {
                extras = new Bundle();
            }

            return new NotificationActionButton(this, extras);
        }

    }

}

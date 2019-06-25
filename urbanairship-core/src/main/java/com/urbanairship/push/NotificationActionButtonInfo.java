package com.urbanairship.push;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.RemoteInput;

/**
 * Notification action button info.
 */
public class NotificationActionButtonInfo {

    private final String buttonId;
    private final boolean isForeground;
    private final Bundle remoteInput;
    private final String description;

    /**
     * Default constructor.
     *
     * @param buttonId The button id.
     * @param isForeground {@code true} if the action should bring the app to the foreground, otherwise {@code false}.
     * @param remoteInput The remote input.
     * @param description The description.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @VisibleForTesting
    public NotificationActionButtonInfo(@NonNull String buttonId,
                                        boolean isForeground,
                                        @Nullable Bundle remoteInput,
                                        @Nullable String description) {

        this.buttonId = buttonId;
        this.isForeground = isForeground;
        this.remoteInput = remoteInput;
        this.description = description;
    }

    static NotificationActionButtonInfo fromIntent(Intent intent) {
        String buttonId = intent.getStringExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID);
        if (buttonId == null) {
            return null;
        }

        boolean isForegroundAction = intent.getBooleanExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, true);
        String description = intent.getStringExtra(PushManager.EXTRA_NOTIFICATION_ACTION_BUTTON_DESCRIPTION);
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);

        return new NotificationActionButtonInfo(buttonId, isForegroundAction, remoteInput, description);
    }

    /**
     * Returns the button's ID.
     *
     * @return The button's ID.
     */
    @NonNull
    public String getButtonId() {
        return buttonId;
    }

    /**
     * If the button should trigger a foreground action or not.
     *
     * @return {@code true} if the action should trigger a foreground action, otherwise {@code false}.
     */
    public boolean isForeground() {
        return isForeground;
    }

    /**
     * Remote input associated with the notification action. Only available if the action
     * button defines {@link com.urbanairship.push.notifications.LocalizableRemoteInput}
     * and the button was triggered from an Android Wear device or Android N.
     *
     * @return The remote input associated with the action button or null.
     */
    @Nullable
    public Bundle getRemoteInput() {
        return remoteInput;
    }

    /**
     * The action's description.
     *
     * @return The action's description.
     */
    @Nullable
    public String getDescription() {
        return description;
    }

    @NonNull
    @Override
    public String toString() {
        return "NotificationActionButtonInfo{" +
                "buttonId='" + buttonId + '\'' +
                ", isForeground=" + isForeground +
                ", remoteInput=" + remoteInput +
                ", description='" + description + '\'' +
                '}';
    }

}

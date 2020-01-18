package com.urbanairship.push;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Internal listener used by Airship Components to listen for notifications.
 */
public interface InternalNotificationListener {

    /**
     * Called when a notification received a response.
     *
     * @param notificationInfo The notification info.
     * @param actionButtonInfo The optional action button if the response was from a button.
     */
    void onNotificationResponse(@NonNull NotificationInfo notificationInfo, @Nullable NotificationActionButtonInfo actionButtonInfo);

}

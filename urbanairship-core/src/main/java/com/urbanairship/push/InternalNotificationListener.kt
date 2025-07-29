package com.urbanairship.push

/**
 * Internal listener used by Airship Components to listen for notifications.
 */
public fun interface InternalNotificationListener {

    /**
     * Called when a notification received a response.
     *
     * @param notificationInfo The notification info.
     * @param actionButtonInfo The optional action button if the response was from a button.
     */
    public fun onNotificationResponse(
        notificationInfo: NotificationInfo, actionButtonInfo: NotificationActionButtonInfo?
    )
}

package com.urbanairship.automation.rewrite.inappmessage

import androidx.annotation.MainThread

/**
 * Message display delegate
 */
public interface InAppMessageDisplayDelegate {

    /**
     * Called to check if the message is ready to be displayed. This method will be called for
     * every message that is pending display whenever a display condition changes. Use `notifyDisplayConditionsChanged`
     * to notify whenever a condition changes to reevaluate the pending In-App messages.
     * @param message: The message
     * @param scheduleID: The schedule ID
     * @return: `true` if the message is ready to display, `false` otherwise.
     */
    @MainThread
    public fun isMessageReadyToDisplay(message: InAppMessage, scheduleID: String): Boolean

    /**
     * Called when a message will be displayed.
     * @param message: The message
     * @param scheduleID: The schedule ID
     */
    @MainThread
    public fun messageWillDisplay(message: InAppMessage, scheduleID: String)

    /**
     * Called when a message finished displaying
     * @param message: The message
     * @param scheduleID: The schedule ID
     */
    @MainThread
    public fun messageFinishedDisplaying(message: InAppMessage, scheduleID: String)
}

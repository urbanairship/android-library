/* Copyright Airship and Contributors */

package com.urbanairship.iam

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
     * @param scheduleId: The schedule ID
     * @return: `true` if the message is ready to display, `false` otherwise.
     */
    @MainThread
    public fun isMessageReadyToDisplay(message: InAppMessage, scheduleId: String): Boolean

    /**
     * Called when a message will be displayed.
     * @param message: The message
     * @param scheduleId: The schedule ID
     */
    @MainThread
    public fun messageWillDisplay(message: InAppMessage, scheduleId: String)

    /**
     * Called when a message finished displaying
     * @param message: The message
     * @param scheduleId: The schedule ID
     */
    @MainThread
    public fun messageFinishedDisplaying(message: InAppMessage, scheduleId: String)
}

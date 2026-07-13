/* Copyright Airship and Contributors */

package com.urbanairship.iam.coordinator

import com.urbanairship.app.ActivityMonitor
import com.urbanairship.iam.InAppMessage
import kotlinx.coroutines.flow.StateFlow

internal class ImmediateDisplayCoordinator(
    activityMonitor: ActivityMonitor,
    private val activityTracker: DisplayActivityTracker,
    private val onMessageWillDisplay: (String) -> Unit = {}
) : DisplayCoordinator {

    override val isReady: StateFlow<Boolean> = activityMonitor.foregroundState

    override fun messageWillDisplay(message: InAppMessage, scheduleId: String) {
        onMessageWillDisplay(scheduleId)
        activityTracker.messageWillDisplay()
    }

    override fun messageFinishedDisplaying(message: InAppMessage, scheduleId: String) {
        activityTracker.messageFinishedDisplaying()
    }
}

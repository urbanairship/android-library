/* Copyright Airship and Contributors */

package com.urbanairship.iam.coordinator

import com.urbanairship.app.ActivityMonitor
import com.urbanairship.iam.InAppMessage
import kotlinx.coroutines.flow.StateFlow

internal class ImmediateDisplayCoordinator(
    activityMonitor: ActivityMonitor,
    private val defaultCoordinator: DefaultDisplayCoordinator
) : DisplayCoordinator {

    override val isReady: StateFlow<Boolean> = activityMonitor.foregroundState

    override fun messageWillDisplay(message: InAppMessage) {
        defaultCoordinator.messageWillDisplay(message)
    }

    override fun messageFinishedDisplaying(message: InAppMessage) {
        defaultCoordinator.messageFinishedDisplaying(message)
    }
}

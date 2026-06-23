/* Copyright Airship and Contributors */

package com.urbanairship.iam.coordinator

import com.urbanairship.app.ActivityMonitor
import com.urbanairship.iam.InAppMessage
import com.urbanairship.util.combineStates
import kotlinx.coroutines.flow.StateFlow

internal class ImmediateDisplayCoordinator(
    activityMonitor: ActivityMonitor,
    private val defaultCoordinator: DefaultDisplayCoordinator
) : DisplayCoordinator {

    override val isReady: StateFlow<Boolean> = combineStates(
        activityMonitor.foregroundState,
        defaultCoordinator.hasActiveDisplays
    ) { foregroundState, hasActiveDisplays ->
        foregroundState && !hasActiveDisplays
    }

    override fun messageWillDisplay(message: InAppMessage, scheduleId: String) {
        defaultCoordinator.messageWillDisplay(message, scheduleId)
    }

    override fun messageFinishedDisplaying(message: InAppMessage, scheduleId: String) {
        defaultCoordinator.messageFinishedDisplaying(message, scheduleId)
    }
}

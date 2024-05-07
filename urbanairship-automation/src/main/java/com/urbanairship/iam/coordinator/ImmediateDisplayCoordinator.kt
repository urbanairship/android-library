package com.urbanairship.iam.coordinator

import com.urbanairship.app.ActivityMonitor
import com.urbanairship.iam.InAppMessage
import kotlinx.coroutines.flow.StateFlow

internal class ImmediateDisplayCoordinator(
    activityMonitor: ActivityMonitor
) : DisplayCoordinator {

    override val isReady: StateFlow<Boolean> = activityMonitor.foregroundState

    override fun messageWillDisplay(message: InAppMessage) { }

    override fun messageFinishedDisplaying(message: InAppMessage) { }
}

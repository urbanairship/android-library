package com.urbanairship.automation.rewrite.inappmessage.displaycoordinator

import com.urbanairship.app.ActivityMonitor
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage
import kotlinx.coroutines.flow.StateFlow

internal class ImmediateDisplayCoordinator(
    activityMonitor: ActivityMonitor
) : DisplayCoordinator {

    override val isReady: StateFlow<Boolean> = activityMonitor.foregroundState

    override fun messageWillDisplay(message: InAppMessage) { }

    override fun messageFinishedDisplaying(message: InAppMessage) { }
}

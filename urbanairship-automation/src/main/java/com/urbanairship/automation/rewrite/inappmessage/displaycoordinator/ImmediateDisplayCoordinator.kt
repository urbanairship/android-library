package com.urbanairship.automation.rewrite.inappmessage.displaycoordinator

import com.urbanairship.automation.rewrite.inappmessage.InAppActivityMonitor
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage

internal class ImmediateDisplayCoordinator(
    private val activityMonitor: InAppActivityMonitor
) : DisplayCoordinatorInterface {

    override fun getIsReady(): Boolean {
        return activityMonitor.isAppForegrounded
    }

    override fun messageWillDisplay(message: InAppMessage) { }

    override fun messageFinishedDisplaying(message: InAppMessage) { }

    override suspend fun waitForReady() {
        activityMonitor.waitForActive()
    }
}

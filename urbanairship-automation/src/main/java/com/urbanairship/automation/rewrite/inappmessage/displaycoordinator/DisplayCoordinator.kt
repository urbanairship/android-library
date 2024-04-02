package com.urbanairship.automation.rewrite.inappmessage.displaycoordinator

import androidx.annotation.MainThread
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage

@MainThread
internal interface DisplayCoordinatorInterface {
    var isReady: Boolean
    fun messageWillDisplay(message: InAppMessage)
    fun messageFinishedDisplaying(message: InAppMessage)
    suspend fun waitForReady()
}

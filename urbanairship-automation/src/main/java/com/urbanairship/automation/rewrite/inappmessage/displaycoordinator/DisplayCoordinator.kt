package com.urbanairship.automation.rewrite.inappmessage.displaycoordinator

import androidx.annotation.MainThread
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage
import kotlinx.coroutines.flow.StateFlow

internal interface DisplayCoordinator {

    val isReady: StateFlow<Boolean>

    @MainThread
    fun messageWillDisplay(message: InAppMessage)

    @MainThread
    fun messageFinishedDisplaying(message: InAppMessage)
}

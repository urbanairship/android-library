/* Copyright Airship and Contributors */

package com.urbanairship.iam.coordinator

import androidx.annotation.MainThread
import com.urbanairship.iam.InAppMessage
import kotlinx.coroutines.flow.StateFlow

internal interface DisplayCoordinator {

    val isReady: StateFlow<Boolean>

    @MainThread
    fun messageWillDisplay(message: InAppMessage)

    @MainThread
    fun messageFinishedDisplaying(message: InAppMessage)
}

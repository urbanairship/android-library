package com.urbanairship.automation.rewrite.inappmessage.displaycoordinator

import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@MainThread
public interface DisplayCoordinatorInterface {
    public fun getIsReady(): Boolean
    public fun messageWillDisplay(message: InAppMessage)
    public fun messageFinishedDisplaying(message: InAppMessage)
    public suspend fun waitForReady()
}

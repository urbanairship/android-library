package com.urbanairship.automation.rewrite.inappmessage.displaycoordinator

import com.urbanairship.PreferenceDataStore
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage

internal interface DisplayCoordinatorManagerInterface {
    var displayInterval: Long
    fun displayCoordinator(message: InAppMessage): DisplayCoordinatorInterface
}

// TODO: DisplayCoordinatorManager

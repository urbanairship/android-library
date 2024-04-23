package com.urbanairship.automation.rewrite.inappmessage.displaycoordinator

import com.urbanairship.PreferenceDataStore
import com.urbanairship.automation.rewrite.inappmessage.InAppActivityMonitor
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage

internal interface DisplayCoordinatorManagerInterface {
    var displayInterval: Long
    fun displayCoordinator(message: InAppMessage): DisplayCoordinatorInterface
}

internal class DisplayCoordinatorManager(
    private val dataStore: PreferenceDataStore,
    activityMonitor: InAppActivityMonitor,
    private val immediateCoordinator: ImmediateDisplayCoordinator = ImmediateDisplayCoordinator(activityMonitor),
    private val defaultCoordinator: DefaultDisplayCoordinator = defaultCoordinator(dataStore, activityMonitor)
): DisplayCoordinatorManagerInterface {

    override var displayInterval: Long
        get() { return dataStore.getLong(DISPLAY_INTERVAL_KEY, 0) }
        set(value) {
            dataStore.put(DISPLAY_INTERVAL_KEY, value)
            defaultCoordinator.displayInterval = value
        }

    override fun displayCoordinator(message: InAppMessage): DisplayCoordinatorInterface {
        if (message.isEmbedded()) { return immediateCoordinator }

        return when(message.displayBehavior) {
            InAppMessage.DisplayBehavior.IMMEDIATE -> immediateCoordinator
            else -> defaultCoordinator
        }
    }

    private companion object {
        const val DISPLAY_INTERVAL_KEY = "UAInAppMessageManagerDisplayInterval"

        fun defaultCoordinator(dataStore: PreferenceDataStore, activityMonitor: InAppActivityMonitor): DefaultDisplayCoordinator {
            return DefaultDisplayCoordinator(
                displayInterval = dataStore.getLong(DISPLAY_INTERVAL_KEY, 0),
                activityMonitor = activityMonitor)
        }
    }
}

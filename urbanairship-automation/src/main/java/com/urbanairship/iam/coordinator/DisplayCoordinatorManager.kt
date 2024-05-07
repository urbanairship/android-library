package com.urbanairship.iam.coordinator

import com.urbanairship.PreferenceDataStore
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.iam.InAppMessage
import kotlin.time.Duration.Companion.seconds

internal class DisplayCoordinatorManager(
    private val dataStore: PreferenceDataStore,
    activityMonitor: ActivityMonitor,
    private val immediateCoordinator: ImmediateDisplayCoordinator = ImmediateDisplayCoordinator(activityMonitor),
    private val defaultCoordinator: DefaultDisplayCoordinator = defaultCoordinator(dataStore, activityMonitor)
) {
    var displayInterval: Long
        get() { return dataStore.getLong(DISPLAY_INTERVAL_KEY, 0) }
        set(value) {
            dataStore.put(DISPLAY_INTERVAL_KEY, value)
            defaultCoordinator.displayInterval = value.seconds
        }

    fun displayCoordinator(message: InAppMessage): DisplayCoordinator {
        if (message.isEmbedded()) { return immediateCoordinator }

        return when(message.displayBehavior) {
            InAppMessage.DisplayBehavior.IMMEDIATE -> immediateCoordinator
            else -> defaultCoordinator
        }
    }

    private companion object {
        const val DISPLAY_INTERVAL_KEY = "UAInAppMessageManagerDisplayInterval"

        fun defaultCoordinator(dataStore: PreferenceDataStore, activityMonitor: ActivityMonitor): DefaultDisplayCoordinator {
            return DefaultDisplayCoordinator(
                displayInterval = dataStore.getLong(DISPLAY_INTERVAL_KEY, 0).seconds,
                activityMonitor = activityMonitor
            )
        }
    }
}

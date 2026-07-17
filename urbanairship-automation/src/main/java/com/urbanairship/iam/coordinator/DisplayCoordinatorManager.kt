/* Copyright Airship and Contributors */

package com.urbanairship.iam.coordinator

import com.urbanairship.preferences.PreferenceStore
import com.urbanairship.preferences.SyncPrefKey
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.iam.InAppMessage
import kotlin.time.Duration.Companion.seconds

internal class DisplayCoordinatorManager(
    private val dataStore: PreferenceStore,
    activityMonitor: ActivityMonitor,
    activityTracker: DisplayActivityTracker = DisplayActivityTracker(),
    private val defaultCoordinator: DefaultDisplayCoordinator =
        defaultCoordinator(dataStore, activityMonitor, activityTracker),
    private val immediateCoordinator: ImmediateDisplayCoordinator =
        ImmediateDisplayCoordinator(activityMonitor, activityTracker),
    private val embeddedCoordinator: EmbeddedDisplayCoordinator = EmbeddedDisplayCoordinator(activityMonitor)
) {
    var displayInterval: Long
        get() = dataStore.get(DISPLAY_INTERVAL_KEY) ?: 0
        set(value) {
            dataStore.put(DISPLAY_INTERVAL_KEY, value)
            defaultCoordinator.displayInterval = value.seconds
        }

    fun displayCoordinator(message: InAppMessage): DisplayCoordinator {
        if (message.isEmbedded()) {
            return embeddedCoordinator
        }

        return when(message.displayBehavior) {
            InAppMessage.DisplayBehavior.IMMEDIATE -> immediateCoordinator
            else -> defaultCoordinator
        }
    }

    private companion object {
        val DISPLAY_INTERVAL_KEY = SyncPrefKey.long("UAInAppMessageManagerDisplayInterval")

        fun defaultCoordinator(
            dataStore: PreferenceStore,
            activityMonitor: ActivityMonitor,
            activityTracker: DisplayActivityTracker
        ): DefaultDisplayCoordinator {
            return DefaultDisplayCoordinator(
                displayInterval = (dataStore.get(DISPLAY_INTERVAL_KEY) ?: 0).seconds,
                activityMonitor = activityMonitor,
                activityTracker = activityTracker
            )
        }

    }
}

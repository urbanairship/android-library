/* Copyright Airship and Contributors */

package com.urbanairship.iam.coordinator

import com.urbanairship.preferences.PreferenceStore
import com.urbanairship.preferences.SyncPrefKey
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.iam.InAppMessage
import kotlin.time.Duration
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
    /** Stored as whole seconds, which is the unit this key has always held. */
    var displayInterval: Duration
        get() = (dataStore.get(DISPLAY_INTERVAL_KEY) ?: 0).seconds
        set(value) {
            dataStore.put(DISPLAY_INTERVAL_KEY, value.inWholeSeconds)
            defaultCoordinator.displayInterval = value
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

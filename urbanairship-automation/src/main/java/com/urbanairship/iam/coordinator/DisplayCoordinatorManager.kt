/* Copyright Airship and Contributors */

package com.urbanairship.iam.coordinator

import com.urbanairship.preferences.PreferenceStore
import com.urbanairship.preferences.SyncPrefKey
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.iam.InAppMessage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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
    /**
     * Stored as whole milliseconds, so that the persisted value and the value the coordinator
     * is using are the same after a `Duration` with sub-second precision is set.
     */
    var displayInterval: Duration
        get() = storedDisplayInterval(dataStore)
        set(value) {
            dataStore.put(DISPLAY_INTERVAL_KEY, value.inWholeMilliseconds)
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
        val DISPLAY_INTERVAL_KEY = SyncPrefKey.long("UAInAppMessageManagerDisplayIntervalMs")

        /**
         * The interval was stored in whole seconds through SDK 20.x. It is read when
         * [DISPLAY_INTERVAL_KEY] is unset, so an upgrade keeps a previously set interval.
         */
        val LEGACY_DISPLAY_INTERVAL_SECONDS_KEY =
            SyncPrefKey.long("UAInAppMessageManagerDisplayInterval")

        fun storedDisplayInterval(dataStore: PreferenceStore): Duration =
            dataStore.get(DISPLAY_INTERVAL_KEY)?.milliseconds
                ?: dataStore.get(LEGACY_DISPLAY_INTERVAL_SECONDS_KEY)?.seconds
                ?: Duration.ZERO

        fun defaultCoordinator(
            dataStore: PreferenceStore,
            activityMonitor: ActivityMonitor,
            activityTracker: DisplayActivityTracker
        ): DefaultDisplayCoordinator {
            return DefaultDisplayCoordinator(
                displayInterval = storedDisplayInterval(dataStore),
                activityMonitor = activityMonitor,
                activityTracker = activityTracker
            )
        }

    }
}

package com.urbanairship.automation.rewrite.engine

import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.analytics.Analytics
import com.urbanairship.analytics.AnalyticsListener
import com.urbanairship.analytics.CustomEvent
import com.urbanairship.analytics.Event
import com.urbanairship.analytics.location.RegionEvent
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.ApplicationListener
import com.urbanairship.automation.rewrite.AutomationAppState
import com.urbanairship.automation.rewrite.AutomationDelay
import com.urbanairship.automation.rewrite.utils.TaskSleeper
import com.urbanairship.util.Clock
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.yield

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AutomationDelayProcessorInterface {
    public suspend fun process(delay: AutomationDelay?, triggerDate: Long)
    public suspend fun areConditionsMet(delay: AutomationDelay?): Boolean
}

internal class AutomationDelayProcessor(
    analytics: Analytics,
    appStateTracker: ActivityMonitor,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    private val sleeper: TaskSleeper = TaskSleeper.default
) : AutomationDelayProcessorInterface {

    private val tracker: StateTracker = StateTracker(analytics, appStateTracker)

    override suspend fun process(delay: AutomationDelay?, triggerDate: Long) {
        if (delay == null) {
            return
        }

        val wait = remainingSeconds(delay, triggerDate)
        if (wait > 0) {
            sleeper.sleep(wait)
        }

        while (!isCancelled() && !areConditionsMet(delay)) {
            if (delay.appState != null && delay.appState != tracker.currentAppState) {
                for (state in tracker.stateUpdates) {
                    if (isCancelled() || state == delay.appState) {
                        break
                    }
                }
            }

            if (isCancelled()) { break }

            if (delay.screens != null && !delay.screens.contains(tracker.currentScreen)) {
                for (screen in tracker.screensUpdate) {
                    if (isCancelled() || delay.screens.contains(screen)) {
                        break
                    }
                }
            }

            if (isCancelled()) { break }

            if (delay.regionID != null && !tracker.regionIDs.contains(delay.regionID)) {
                for (update in tracker.regionUpdate) {
                    if (isCancelled() || update.contains(delay.regionID)) {
                        break
                    }
                }
            }
        }
    }

    private suspend fun isCancelled(): Boolean {
        try {
            yield()
            return false
        } catch (ex: CancellationException) {
            finalize()
            return true
        }
    }

    private fun finalize() {
        tracker.unsubscribe()
    }

    override suspend fun areConditionsMet(delay: AutomationDelay?): Boolean {
        if (delay == null) {
            return true
        }

        if (delay.appState != null && delay.appState != tracker.currentAppState) {
            return false
        }

        if (!delay.screens.isNullOrEmpty()) {
            if (tracker.currentScreen == null || !delay.screens.contains(tracker.currentScreen)) {
                return false
            }
        }

        if (delay.regionID != null) {
            if (!tracker.regionIDs.contains(delay.regionID)) {
                return false
            }
        }

        return true
    }

    private fun remainingSeconds(delay: AutomationDelay, triggerDate: Long): Long {
        val seconds = delay.seconds ?: return 0
        val remaining = seconds - TimeUnit.MILLISECONDS.toSeconds(clock.currentTimeMillis() - triggerDate)
        return max(0, remaining)
    }
}

private class StateTracker(
    private val analytics: Analytics,
    private val monitor: ActivityMonitor
) {
    var currentScreen: String? = null
        private set

    private val screens = Channel<String>(CONFLATED)
    val screensUpdate: ReceiveChannel<String> = screens

    var currentAppState: AutomationAppState
        private set
    private val state = Channel<AutomationAppState>(CONFLATED)
    val stateUpdates: ReceiveChannel<AutomationAppState> = state

    val regionIDs = mutableSetOf<String>()
    private val regions = Channel<Set<String>>(CONFLATED)
    val regionUpdate: ReceiveChannel<Set<String>> = regions

    val unsubscribe: () -> Unit

    init {
        val analyticsListener = object : AnalyticsListener {
            override fun onScreenTracked(screenName: String) {
                currentScreen = screenName
                if (!screens.trySend(screenName).isSuccess) {
                    UALog.e { "Failed to send screen name" }
                }
            }

            override fun onRegionEventAdded(event: RegionEvent) {
                if (event.boundaryEvent == RegionEvent.BOUNDARY_EVENT_ENTER) {
                    regionIDs.add(event.regionId)
                } else {
                    regionIDs.remove(event.regionId)
                }
                if (!regions.trySend(regionIDs).isSuccess) {
                    UALog.e { "Failed to send region updates" }
                }
            }

            override fun onCustomEventAdded(event: CustomEvent) {}
            override fun onFeatureFlagInteractedEventAdded(event: Event) {}
        }

        analytics.addAnalyticsListener(analyticsListener)

        currentAppState = if (monitor.isAppForegrounded) {
            AutomationAppState.FOREGROUND
        } else {
            AutomationAppState.BACKGROUND
        }

        val stateListener = object : ApplicationListener {
            override fun onForeground(milliseconds: Long) {
                currentAppState = AutomationAppState.FOREGROUND
                if (!state.trySend(currentAppState).isSuccess) {
                    UALog.e { "Failed to send app state update" }
                }
            }

            override fun onBackground(milliseconds: Long) {
                currentAppState = AutomationAppState.BACKGROUND
                if (!state.trySend(currentAppState).isSuccess) {
                    UALog.e { "Failed to send app state update" }
                }
            }
        }
        monitor.addApplicationListener(stateListener)

        unsubscribe = {
            monitor.removeApplicationListener(stateListener)
            analytics.removeAnalyticsListener(analyticsListener)
            regions.close()
            state.close()
            regions.close()
        }
    }
}

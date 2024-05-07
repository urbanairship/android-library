package com.urbanairship.automation.engine

import androidx.annotation.RestrictTo
import com.urbanairship.analytics.Analytics
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.automation.AutomationAppState
import com.urbanairship.automation.AutomationDelay
import com.urbanairship.automation.utils.TaskSleeper
import com.urbanairship.util.Clock
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AutomationDelayProcessorInterface {
    public suspend fun process(delay: AutomationDelay?, triggerDate: Long)
    public fun areConditionsMet(delay: AutomationDelay?): Boolean
}

internal class AutomationDelayProcessor(
    private val analytics: Analytics,
    private val activityMonitor: ActivityMonitor,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    private val sleeper: TaskSleeper = TaskSleeper.default
) : AutomationDelayProcessorInterface {


    override suspend fun process(delay: AutomationDelay?, triggerDate: Long) = withContext(Dispatchers.Main.immediate) {
        if (delay == null) {
            return@withContext
        }

        val wait = remainingSeconds(delay, triggerDate)
        if (wait > 0) {
            sleeper.sleep(wait.seconds)
        }

        while (isActive && !areConditionsMet(delay)) {
            yield()

            if (!isAppStateMatch(delay)) {
                activityMonitor.foregroundState.filter {
                    it == (delay.appState == AutomationAppState.FOREGROUND)
                }.first()
            }

            if (!isActive) { break }

            if (!isScreenMatch(delay)) {
                analytics.screenState.filter {
                    delay.screens?.contains(it) ?: true
                }.first()
            }

            if (!isActive) { break }

            if (!isRegionMatch(delay)) {
                analytics.regionState.filter {
                    it.contains(delay.regionID)
                }.first()
            }
        }
    }

    override fun areConditionsMet(delay: AutomationDelay?): Boolean {
        if (delay == null) {  return true  }
        return isAppStateMatch(delay) && isScreenMatch(delay) && isRegionMatch(delay)
    }

    private fun isAppStateMatch(delay: AutomationDelay): Boolean {
        if (delay.appState == null) { return true }
        return (delay.appState == AutomationAppState.FOREGROUND) == activityMonitor.foregroundState.value
    }

    private fun isScreenMatch(delay: AutomationDelay): Boolean {
        if (delay.screens.isNullOrEmpty()) { return true }
        return delay.screens.contains(analytics.screenState.value)
    }

    private fun isRegionMatch(delay: AutomationDelay): Boolean {
        if (delay.regionID.isNullOrEmpty()) { return true }
        return analytics.regionState.value.contains(delay.regionID)
    }

    private fun remainingSeconds(delay: AutomationDelay, triggerDate: Long): Long {
        val seconds = delay.seconds ?: return 0
        val remaining = seconds - TimeUnit.MILLISECONDS.toSeconds(clock.currentTimeMillis() - triggerDate)
        return max(0, remaining)
    }
}

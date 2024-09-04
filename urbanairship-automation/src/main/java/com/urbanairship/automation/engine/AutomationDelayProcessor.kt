/* Copyright Airship and Contributors */

package com.urbanairship.automation.engine

import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import com.urbanairship.analytics.Analytics
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.automation.AutomationAppState
import com.urbanairship.automation.AutomationDelay
import com.urbanairship.automation.ExecutionWindowProcessor
import com.urbanairship.util.Clock
import com.urbanairship.util.TaskSleeper
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal interface AutomationDelayProcessorInterface {
    suspend fun preprocess(delay: AutomationDelay?, triggerDate: Long)
    suspend fun process(delay: AutomationDelay?, triggerDate: Long)
    @MainThread
    fun areConditionsMet(delay: AutomationDelay?): Boolean
}

internal class AutomationDelayProcessor(
    private val analytics: Analytics,
    private val activityMonitor: ActivityMonitor,
    private val executionWindowProcessor: ExecutionWindowProcessor,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    private val sleeper: TaskSleeper = TaskSleeper.default
) : AutomationDelayProcessorInterface {

    companion object {
        private val PREPROCESS_DELAY_ALLOWANCE = 30.seconds

    }
    override suspend fun preprocess(delay: AutomationDelay?, triggerDate: Long) {
        if (delay == null) {
            return
        }

        return coroutineScope {
            ensureActive()

            val wait = remainingDelay(delay, triggerDate) - PREPROCESS_DELAY_ALLOWANCE
            if (wait.isPositive()) {
                sleeper.sleep(wait)
            }

            ensureActive()

            delay.executionWindow?.let {
                executionWindowProcessor.process(it)
            }
        }
    }

    override suspend fun process(
        delay: AutomationDelay?,
        triggerDate: Long
    ) = withContext(Dispatchers.Main.immediate) {
        ensureActive()

        if (delay == null) {
            return@withContext
        }

        val wait = remainingDelay(delay, triggerDate)
        if (wait.isPositive()) {
            sleeper.sleep(wait)
        }

        while (isActive && !areConditionsMet(delay)) {
            yield()

            ensureActive()

            if (!isAppStateMatch(delay)) {
                activityMonitor.foregroundState.filter {
                    it == (delay.appState == AutomationAppState.FOREGROUND)
                }.first()
            }

            ensureActive()

            if (!isScreenMatch(delay)) {
                analytics.screenState.filter {
                    delay.screens?.contains(it) ?: true
                }.first()
            }

            ensureActive()

            if (!isRegionMatch(delay)) {
                analytics.regionState.filter {
                    it.contains(delay.regionId)
                }.first()
            }

            ensureActive()

            if (delay.executionWindow != null) {
                executionWindowProcessor.process(delay.executionWindow)
            }
        }
    }

    override fun areConditionsMet(delay: AutomationDelay?): Boolean {
        if (delay == null) {  return true  }
        return isAppStateMatch(delay) && isScreenMatch(delay)
                && isRegionMatch(delay) && isDisplayWindowMatch(delay)
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
        if (delay.regionId.isNullOrEmpty()) { return true }
        return analytics.regionState.value.contains(delay.regionId)
    }

    private fun isDisplayWindowMatch(delay: AutomationDelay): Boolean {
        val window = delay.executionWindow ?: return true
        return executionWindowProcessor.isActive(window)
    }

    private fun remainingDelay(delay: AutomationDelay, triggerDate: Long): Duration {
        val seconds = delay.seconds ?: return 0.seconds
        val remaining = seconds - TimeUnit.MILLISECONDS.toSeconds(clock.currentTimeMillis() - triggerDate)
        return max(0, remaining).seconds
    }
}

/* Copyright Airship and Contributors */

package com.urbanairship.util.timer

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.ApplicationListener
import com.urbanairship.util.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ActiveTimer(
    private val appStateTracker: ActivityMonitor,
    private val clock: Clock = Clock.DEFAULT_CLOCK
) : Timer {
    private val _isStarted = MutableStateFlow(false)
    private val isActive = MutableStateFlow(appStateTracker.isAppForegrounded)
    private val startDate = MutableStateFlow<Long?>(null)
    private var elapsedTime: Duration = 0.seconds

    private var cancelListener: (() -> Unit)? = null

    override val time: Duration
        get() { return elapsedTime + currentSessionTime() }

    public val isStarted: Boolean
        get() { return _isStarted.value }

    init {
        val listener = object : ApplicationListener {
            override fun onForeground(milliseconds: Long) {
                isActive.update { true }
                if (_isStarted.value && startDate.value == null) {
                    startDate.update { clock.currentTimeMillis() }
                }
            }

            override fun onBackground(milliseconds: Long) {
                isActive.update { false }
                stop()
            }

        }
        appStateTracker.addApplicationListener(listener)
        cancelListener = { appStateTracker.removeApplicationListener(listener) }
    }

    override fun start() {
        if (_isStarted.value) { return }

        if (isActive.value) {
            startDate.update { clock.currentTimeMillis() }
        }

        _isStarted.update { true }
    }

    override fun stop() {
        if (!_isStarted.value) { return }

        elapsedTime += currentSessionTime()
        startDate.update { null }
        _isStarted.update { false }
    }

    @VisibleForTesting
    internal fun stopListening() {
        cancelListener?.invoke()
    }

    private fun currentSessionTime(): Duration {
        val date = startDate.value ?: return 0.seconds
        return (clock.currentTimeMillis() - date).milliseconds
    }
}

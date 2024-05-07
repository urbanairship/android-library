package com.urbanairship.automation.utils

import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.ApplicationListener
import com.urbanairship.util.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class ActiveTimer(
    private val appStateTracker: ActivityMonitor,
    private val clock: Clock = Clock.DEFAULT_CLOCK
) {
    private val _isStarted = MutableStateFlow(false)
    private val isActive = MutableStateFlow(appStateTracker.isAppForegrounded)
    private val startDate = MutableStateFlow<Long?>(null)
    private var elapsedTime: Long = 0

    private var cancelListener: (() -> Unit)? = null

    val time: Long
        get() { return elapsedTime + currentSessionTime() }

    val isStarted: Boolean
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

    fun start() {
        if (_isStarted.value) { return }

        if (isActive.value) {
            startDate.update { clock.currentTimeMillis() }
        }

        _isStarted.update { true }
    }

    fun stop() {
        if (!_isStarted.value) { return }

        elapsedTime += currentSessionTime()
        startDate.update { null }
        _isStarted.update { false }
    }

    fun stopListening() {
        cancelListener?.invoke()
    }

    private fun currentSessionTime(): Long {
        val date = startDate.value ?: return 0
        return clock.currentTimeMillis() - date

    }
}

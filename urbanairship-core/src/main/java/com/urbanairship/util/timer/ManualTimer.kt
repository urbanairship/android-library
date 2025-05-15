/* Copyright Airship and Contributors */

package com.urbanairship.util.timer

import androidx.annotation.RestrictTo
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
public class ManualTimer(
    private val clock: Clock = Clock.DEFAULT_CLOCK
) : Timer {
    private val _isStarted = MutableStateFlow(false)
    private val startDate = MutableStateFlow<Long?>(null)
    private var elapsed: Duration = 0.seconds

    override val time: Duration
        get() { return elapsed + currentSessionTime() }

    public val isStarted: Boolean
        get() { return _isStarted.value }

    override fun start() {
        if (_isStarted.value) { return }

        startDate.update { clock.currentTimeMillis() }
        _isStarted.update { true }
    }

    override fun stop() {
        if (!_isStarted.value) { return }

        elapsed += currentSessionTime()
        startDate.update { null }
        _isStarted.update { false }
    }

    private fun currentSessionTime(): Duration {
        val date = startDate.value ?: return 0.seconds
        return (clock.currentTimeMillis() - date).milliseconds
    }
}

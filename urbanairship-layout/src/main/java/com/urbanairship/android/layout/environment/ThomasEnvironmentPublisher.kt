/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.environment

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds a layout's `$environment` and decides when a new reading of it is published.
 *
 * A change to anything but a size — the window's, the safe window's or the frame's — publishes
 * at once. A size change publishes at the start and end of a resize burst: the first change goes
 * out immediately, and the rest wait until the size has held still for [quietPeriodMillis], then
 * the latest goes out.
 * A window dragged continuously re-evaluates the tree twice rather than on every step, and a
 * rotation, a single change, applies at once.
 *
 * @param quietPeriodMillis how long the size must hold still to end a burst.
 * @param handler where the end of a burst is scheduled; the main thread's.
 */
internal class ThomasEnvironmentPublisher(
    private val quietPeriodMillis: Long = DEFAULT_QUIET_PERIOD_MILLIS,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {

    private val _environment = MutableStateFlow<ThomasEnvironment?>(null)

    /** The environment last published, or null before the first. */
    val environment: StateFlow<ThomasEnvironment?> = _environment.asStateFlow()

    /** The latest size change waiting for its burst to end, if one arrived after the first. */
    private var pending: ThomasEnvironment? = null
    private var inBurst = false
    private val endBurst = Runnable {
        inBurst = false
        pending?.let { _environment.value = it }
        pending = null
    }

    /**
     * Reads the environment from [context] and [frame] and publishes it, now or at the end of a
     * resize burst.
     *
     * @param context a context of the activity the layout is shown in.
     * @param frame the frame the root view last reported, or null before it has been laid out.
     */
    @MainThread
    fun publish(context: Context, frame: EnvironmentFrame?) {
        publish(ThomasEnvironment.from(context, frame))
    }

    /** @see publish */
    @MainThread
    fun publish(reading: ThomasEnvironment) {
        val latest = pending ?: _environment.value
        if (latest == null) {
            _environment.value = reading
            return
        }
        if (reading == latest) return

        if (!reading.differsOnlyInSize(latest)) {
            handler.removeCallbacks(endBurst)
            inBurst = false
            pending = null
            _environment.value = reading
            return
        }

        if (inBurst) pending = reading else _environment.value = reading
        inBurst = true
        handler.removeCallbacks(endBurst)
        handler.postDelayed(endBurst, quietPeriodMillis)
    }

    private companion object {
        const val DEFAULT_QUIET_PERIOD_MILLIS = 150L
    }
}

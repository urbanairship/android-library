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
 * at once. Size changes publish at most once per [intervalMillis]: a size change with no publish
 * in the last interval goes out immediately, and later ones wait for the interval to end, when
 * only the latest goes out. A window dragged continuously for 1s re-evaluates the tree about 10
 * times at the default interval, and the final size always goes out. A change of orientation is
 * not a size change and applies at once.
 *
 * @param intervalMillis the shortest time between two published size changes.
 * @param handler where the end of an interval is scheduled; the main thread's.
 */
internal class ThomasEnvironmentPublisher(
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {

    private val _environment = MutableStateFlow<ThomasEnvironment?>(null)

    /** The environment last published, or null before the first. */
    val environment: StateFlow<ThomasEnvironment?> = _environment.asStateFlow()

    /** The latest size change waiting for the current interval to end. */
    private var pending: ThomasEnvironment? = null
    private var inInterval = false
    private val endInterval = Runnable {
        val next = pending
        pending = null
        if (next == null) {
            inInterval = false
        } else {
            _environment.value = next
            scheduleIntervalEnd()
        }
    }

    /**
     * Reads the environment from [context] and [frame] and publishes it, now or at the end of the
     * current interval.
     *
     * @param context a context of the activity the layout is shown in.
     * @param frame the frame the root view last reported, or null before it has been laid out.
     */
    @MainThread
    fun publish(context: Context, frame: EnvironmentFrame?) {
        publish(ThomasEnvironment.from(context, frame))
    }

    /**
     * Publishes a reading from [context] with no frame, unless one has already been published.
     *
     * A root view calls this when it's created, so the first frame has an environment to match
     * before the first layout reports the frame. A root view that replaces another, as after an
     * activity is recreated, leaves the last reading in place until its own first layout.
     *
     * @param context a context of the activity the layout is shown in.
     */
    @MainThread
    fun seed(context: Context) {
        if (_environment.value == null) publish(context, frame = null)
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
            handler.removeCallbacks(endInterval)
            inInterval = false
            pending = null
            _environment.value = reading
            return
        }

        if (inInterval) {
            pending = reading
        } else {
            _environment.value = reading
            scheduleIntervalEnd()
        }
    }

    private fun scheduleIntervalEnd() {
        inInterval = true
        handler.postDelayed(endInterval, intervalMillis)
    }

    private companion object {
        const val DEFAULT_INTERVAL_MILLIS = 100L
    }
}

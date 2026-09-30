/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.environment

import android.content.Context
import android.view.Choreographer
import androidx.annotation.MainThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds a layout's `$environment` and decides when a new reading of it is published.
 *
 * A change to anything but a size — the window's, the safe window's or the frame's — publishes
 * at once. Size changes publish at most once per frame: a size change with no publish since the
 * last frame goes out immediately, and later ones wait for the next frame, when only the latest
 * goes out. A window dragged continuously re-evaluates the tree at most once per frame, and the
 * final size always goes out. A change of orientation is not a size change and applies at once.
 *
 * @param nextFrame where the end of a frame is scheduled; the main thread's [Choreographer].
 */
internal class ThomasEnvironmentPublisher(
    private val nextFrame: NextFrame = ChoreographerNextFrame
) {

    /** Schedules a callback for the start of the next frame. */
    internal interface NextFrame {
        fun post(callback: Choreographer.FrameCallback)
        fun remove(callback: Choreographer.FrameCallback)
    }

    private object ChoreographerNextFrame : NextFrame {
        override fun post(callback: Choreographer.FrameCallback) =
            Choreographer.getInstance().postFrameCallback(callback)

        override fun remove(callback: Choreographer.FrameCallback) =
            Choreographer.getInstance().removeFrameCallback(callback)
    }

    private val _environment = MutableStateFlow<ThomasEnvironment?>(null)

    /** The environment last published, or null before the first. */
    val environment: StateFlow<ThomasEnvironment?> = _environment.asStateFlow()

    /** The latest size change waiting for the next frame. */
    private var pending: ThomasEnvironment? = null
    private var awaitingFrame = false
    private val onFrame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val next = pending
            pending = null
            if (next == null) {
                awaitingFrame = false
            } else {
                _environment.value = next
                nextFrame.post(this)
            }
        }
    }

    /**
     * Reads the environment from [context] and [frame] and publishes it, now or at the next frame.
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
            nextFrame.remove(onFrame)
            awaitingFrame = false
            pending = null
            _environment.value = reading
            return
        }

        if (awaitingFrame) {
            pending = reading
        } else {
            _environment.value = reading
            awaitingFrame = true
            nextFrame.post(onFrame)
        }
    }
}

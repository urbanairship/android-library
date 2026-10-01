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
 * A reading publishes at the start of the next frame, and only the latest of those that arrive
 * before it goes out, so a window dragged continuously re-evaluates the tree at most once per
 * frame and the final size always goes out. Root views report from `onLayout`, and the models'
 * collectors run inline on the main thread: publishing then would update views mid-layout, and
 * every one that requests a layout makes the window run a second measure and layout pass.
 *
 * The first reading, and one published with `immediate`, go out at once.
 *
 * @param nextFrame where the next frame's publish is scheduled; the main thread's [Choreographer].
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

    /** The latest reading waiting for the next frame. */
    private var pending: ThomasEnvironment? = null
    private val onFrame = Choreographer.FrameCallback {
        pending?.let { _environment.value = it }
        pending = null
    }

    /**
     * Reads the environment from [context] and [frame] and publishes it at the next frame.
     *
     * @param context a context of the activity the layout is shown in.
     * @param frame the frame the root view last reported, or null before it has been laid out.
     * @param immediate publishes now instead, replacing any reading waiting for the next frame.
     */
    @MainThread
    fun publish(context: Context, frame: EnvironmentFrame?, immediate: Boolean = false) {
        publish(ThomasEnvironment.from(context, frame), immediate)
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
    fun publish(reading: ThomasEnvironment, immediate: Boolean = false) {
        if (immediate || _environment.value == null) {
            if (pending != null) nextFrame.remove(onFrame)
            pending = null
            _environment.value = reading
            return
        }
        if (reading == (pending ?: _environment.value)) return

        if (pending == null) nextFrame.post(onFrame)
        pending = reading
    }
}

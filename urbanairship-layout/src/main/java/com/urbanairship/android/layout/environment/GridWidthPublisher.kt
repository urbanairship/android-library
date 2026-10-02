/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.environment

import android.view.Choreographer
import androidx.annotation.MainThread

/**
 * Decides when a grid's measured width is published as `$grids.current.width`.
 *
 * Follows [ThomasEnvironmentPublisher]: a width publishes at the start of the next frame, and only
 * the latest of those that arrive before it goes out. The grid reports from `onLayout`, and the
 * collectors inside it run inline on the main thread, so publishing then would update views
 * mid-layout and make the window run a second measure and layout pass.
 *
 * A width published with `immediate` goes out at once. The grid's view is the only writer of
 * [state], and a new view publishes its first width that way.
 *
 * @param state the grid's state, which everything inside the grid reads.
 * @param nextFrame where the next frame's publish is scheduled.
 */
internal class GridWidthPublisher(
    private val state: SharedState<GridState>,
    private val nextFrame: NextFrame = NextFrame.Main
) {

    /** The width last published, which only this publisher writes. */
    private var published: Float? = null

    /** Whether a width is waiting for the next frame; [pending] can be null, for no width. */
    private var hasPending = false
    private var pending: Float? = null
    private val onFrame = Choreographer.FrameCallback {
        if (hasPending) set(pending)
        hasPending = false
    }

    /**
     * Publishes [width] at the next frame.
     *
     * @param width the grid's inner width in points, or null for a grid with none to measure.
     * @param immediate publishes now instead, replacing any width waiting for the next frame; the
     * grid's first layout passes it, so its contents match the width from their first frame.
     */
    @MainThread
    fun publish(width: Float?, immediate: Boolean = false) {
        if (immediate) {
            if (hasPending) nextFrame.remove(onFrame)
            hasPending = false
            set(width)
            return
        }
        if (width == (if (hasPending) pending else published)) return

        if (!hasPending) nextFrame.post(onFrame)
        hasPending = true
        pending = width
    }

    private fun set(width: Float?) {
        published = width
        state.update { it.copy(width = width) }
    }
}

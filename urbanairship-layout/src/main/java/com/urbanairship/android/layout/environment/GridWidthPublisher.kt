/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.environment

import android.view.Choreographer
import androidx.annotation.MainThread

/**
 * Decides when a grid's measured width is published as `$grids.current.width`.
 *
 * Every width publishes at the start of the next frame, and only the latest of those that arrive
 * before it goes out, as [ThomasEnvironmentPublisher] does for a resize. The grid reports from
 * `onLayout`, and the collectors inside it run inline on the main thread: published then, the
 * layouts they request would be dropped when the pass finishes, leaving views sized for the old
 * width. The first width waits too, so until it lands a grid's contents see the width already in
 * [state]: none for a new grid, or the last one published for a view built again.
 *
 * Each view of the grid has its own publisher over the model's [state], and calls [cancel] when it
 * detaches so a width it measured can't land after a newer view's.
 *
 * @param state the grid's state, which everything inside the grid reads.
 * @param nextFrame where the next frame's publish is scheduled.
 */
internal class GridWidthPublisher(
    private val state: SharedState<GridState>,
    private val nextFrame: NextFrame = NextFrame.Main
) {

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
     */
    @MainThread
    fun publish(width: Float?) {
        // Against the shared state, not this view's last publish: another view of the grid may
        // have written since, or a view built again may inherit a width it hasn't measured.
        val latest = if (hasPending) pending else state.changes.value.width
        if (width == latest) return

        if (!hasPending) nextFrame.post(onFrame)
        hasPending = true
        pending = width
    }

    /** Drops a width waiting for the next frame. */
    @MainThread
    fun cancel() {
        if (hasPending) nextFrame.remove(onFrame)
        hasPending = false
    }

    private fun set(width: Float?) {
        state.update { it.copy(width = width) }
    }
}

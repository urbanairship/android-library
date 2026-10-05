/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.environment

import android.view.Choreographer
import androidx.annotation.MainThread
import com.urbanairship.json.JsonMap

/**
 * Decides when a grid's conditions, chosen from its measured width, are published at
 * `$grids.current.conditions`.
 *
 * Conditions are published only when they change, so a resize that crosses no breakpoint changes
 * no state. Every change publishes at the start of the next frame, and only the latest of those that arrive
 * before it goes out, as [ThomasEnvironmentPublisher] does for a resize. The grid reports from
 * `onLayout`, and the collectors inside it run inline on the main thread: published then, the
 * layouts they request would be dropped when the pass finishes, leaving views sized for the old
 * conditions. The first change waits too, so until it lands a grid's contents see the conditions
 * already in [state]: those chosen with no width for a new grid, or the last published for a view
 * built again.
 *
 * Each view of the grid has its own publisher over the model's [state], and calls [cancel] when it
 * detaches so conditions it measured can't land after a newer view's.
 *
 * @param state the grid's state, which everything inside the grid reads.
 * @param nextFrame where the next frame's publish is scheduled.
 */
internal class GridConditionsPublisher(
    private val state: SharedState<GridState>,
    private val nextFrame: NextFrame = NextFrame.Main
) {

    /** The conditions waiting for the next frame, or null when none are. */
    private var pending: JsonMap? = null
    private val onFrame = Choreographer.FrameCallback {
        pending?.let(::set)
        pending = null
    }

    /**
     * Publishes [conditions] at the next frame, unless they're what the grid already holds.
     *
     * @param conditions the grid's conditions as chosen from its measured width.
     */
    @MainThread
    fun publish(conditions: JsonMap) {
        // Against the shared state, not this view's last publish: another view of the grid may
        // have written since, or a view built again may inherit conditions it hasn't measured.
        val latest = pending ?: state.changes.value.conditions
        if (conditions == latest) return

        if (pending == null) nextFrame.post(onFrame)
        pending = conditions
    }

    /** Drops conditions waiting for the next frame. */
    @MainThread
    fun cancel() {
        if (pending != null) nextFrame.remove(onFrame)
        pending = null
    }

    private fun set(conditions: JsonMap) {
        state.update { it.copy(conditions = conditions) }
    }
}

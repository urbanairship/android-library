/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.environment

import com.urbanairship.json.JsonMap
import android.view.Choreographer
import android.view.View
import androidx.annotation.MainThread
import com.urbanairship.android.layout.property.Conditions
import com.urbanairship.android.layout.property.EnvironmentConditions
import com.urbanairship.android.layout.property.choose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds a layout's `$environment` and decides when a new reading of it is published.
 *
 * Each reading is measured, its environment [conditions] chosen, and published as predicates read
 * it: without sizes. A reading that chooses the same values as the last changes nothing, so a
 * resize that crosses no breakpoint never re-evaluates the tree.
 *
 * A reading publishes at the start of the next frame, and only the latest of those that arrive
 * before it goes out, so a window dragged continuously re-evaluates the tree at most once per
 * frame and the final size always goes out. Root views report from `onLayout`, and the models'
 * collectors run inline on the main thread: publishing then would update views mid-layout, and
 * every one that requests a layout makes the window run a second measure and layout pass.
 *
 * The first reading, and one published with `immediate`, go out at once.
 *
 * @param conditions the scene's `environment_state.conditions`.
 * @param nextFrame where the next frame's publish is scheduled.
 */
internal class ThomasEnvironmentPublisher(
    private val conditions: EnvironmentConditions = Conditions(emptyMap()),
    private val nextFrame: NextFrame = NextFrame.Main
) {

    private val _environment = MutableStateFlow<PredicateEnvironment?>(null)

    /** The environment last published, or null before the first. */
    val environment: StateFlow<PredicateEnvironment?> = _environment.asStateFlow()

    /** The latest reading waiting for the next frame. */
    private var pending: PredicateEnvironment? = null
    private val onFrame = Choreographer.FrameCallback {
        pending?.let { _environment.value = it }
        pending = null
    }

    /**
     * Reads the environment from [rootView] and [frame] and publishes it at the next frame.
     *
     * @param rootView the layout's root view; see [ThomasEnvironment.from].
     * @param frame the frame the root view last reported, or null before it has been laid out.
     * @param immediate publishes now instead, replacing any reading waiting for the next frame.
     */
    @MainThread
    fun publish(rootView: View, frame: EnvironmentFrame?, immediate: Boolean = false) {
        publish(ThomasEnvironment.from(rootView, frame), immediate)
    }

    /**
     * Publishes a reading from [rootView] with no frame, unless one has already been published.
     *
     * A root view calls this when it's created, so the first frame has an environment to match
     * before the first layout reports the frame. A root view that replaces another, as after an
     * activity is recreated, leaves the last reading in place until its own first layout.
     *
     * @param rootView the layout's root view; see [ThomasEnvironment.from].
     * @param frame the frame the root view will be laid out at, where the host knows it before
     * layout, as a modal filling its window does.
     */
    @MainThread
    fun seed(rootView: View, frame: EnvironmentFrame? = null) {
        if (_environment.value == null) publish(rootView, frame)
    }

    /** Returns the environment conditions [measured] would choose, without publishing it. */
    fun conditionsFor(measured: ThomasEnvironment): JsonMap = conditions.choose(measured)

    /** @see publish */
    @MainThread
    fun publish(measured: ThomasEnvironment, immediate: Boolean = false) {
        val reading = PredicateEnvironment.of(measured, conditions.choose(measured))
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

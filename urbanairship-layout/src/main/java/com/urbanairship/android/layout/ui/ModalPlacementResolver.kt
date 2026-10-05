/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.ui

import android.content.Context
import android.os.Build
import android.view.View
import com.urbanairship.android.layout.ModalPresentation
import com.urbanairship.android.layout.environment.EnvironmentFrame
import com.urbanairship.android.layout.property.ModalPlacement
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.json.JsonMap

/**
 * Chooses a modal's placement against the frame the modal will fill.
 *
 * From API 28 a modal fills its whole window whatever its placement. Below it the platform keeps a
 * placement that respects the safe area inside the system bars, so the frame depends on the
 * placement: a `frame_*` selector can then choose a placement whose own frame chooses another, and
 * recreating for each would never stop. A placement is stable when its own frame chooses it again,
 * and the modal only moves to a stable one.
 *
 * @param conditionsFor the layout's environment conditions as a reading with this frame chooses them.
 */
internal class ModalPlacementResolver(
    private val context: Context,
    private val presentation: ModalPresentation,
    private val rootView: View,
    private val conditionsFor: (EnvironmentFrame) -> JsonMap
) {

    /** Returns the frame a modal showing [placement] fills. */
    fun frameFor(placement: ModalPlacement): EnvironmentFrame {
        val window = ResourceUtils.getWindowPixels(context, rootView)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P || placement.shouldIgnoreSafeArea()) {
            EnvironmentFrame(window.width, window.height)
        } else {
            EnvironmentFrame(window.safeWidth, window.safeHeight)
        }
    }

    /** Returns whether [placement]'s own frame chooses it again. */
    fun isStable(placement: ModalPlacement): Boolean = resolve(frameFor(placement)) === placement

    /**
     * Returns the placement to open with: the one the whole window chooses when it fills the whole
     * window, else the one the safe window chooses. Where neither is stable, that last one, and
     * [isStable] then keeps the modal from moving off it.
     */
    fun initialPlacement(): ModalPlacement {
        val window = ResourceUtils.getWindowPixels(context, rootView)
        val whole = resolve(EnvironmentFrame(window.width, window.height))
        if (frameFor(whole) == EnvironmentFrame(window.width, window.height)) return whole
        return resolve(EnvironmentFrame(window.safeWidth, window.safeHeight))
    }

    private fun resolve(frame: EnvironmentFrame): ModalPlacement =
        presentation.getResolvedPlacement(context, conditionsFor(frame))
}

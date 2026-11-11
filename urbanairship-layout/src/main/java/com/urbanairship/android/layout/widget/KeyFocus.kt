/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.view.View
import android.view.ViewGroup

/**
 * Returns the focus-order direction to use when a directional search from this view finds
 * nothing: forward for Down and Right, backward for Up and Left.
 *
 * A view the full size of its scene leaves nothing beyond its edges for a directional search to
 * find. Forward order runs right to left in an RTL layout, so there Right maps to backward.
 *
 * @param direction The directional search that found nothing.
 * @return [View.FOCUS_FORWARD] or [View.FOCUS_BACKWARD], or `null` if [direction] isn't an arrow.
 */
internal fun View.inOrderFallback(direction: Int): Int? {
    val isRtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
    return when (direction) {
        View.FOCUS_DOWN -> View.FOCUS_FORWARD
        View.FOCUS_UP -> View.FOCUS_BACKWARD
        View.FOCUS_RIGHT -> if (isRtl) View.FOCUS_BACKWARD else View.FOCUS_FORWARD
        View.FOCUS_LEFT -> if (isRtl) View.FOCUS_FORWARD else View.FOCUS_BACKWARD
        else -> null
    }
}

/**
 * Adds this group's focusable descendants to [views], leaving the group itself out.
 *
 * For a container that's focusable for scrolling or accessibility, but has nothing to do with
 * D-pad or keyboard focus right now: a stop on it would be an invisible one. This only affects
 * input focus search; the group's accessibility node is unchanged.
 */
internal fun ViewGroup.addDescendantFocusables(views: ArrayList<View>, direction: Int, focusableMode: Int) {
    if (descendantFocusability == ViewGroup.FOCUS_BLOCK_DESCENDANTS) return
    for (index in 0 until childCount) {
        val child = getChildAt(index)
        if (child.visibility == View.VISIBLE) {
            child.addFocusables(views, direction, focusableMode)
        }
    }
}

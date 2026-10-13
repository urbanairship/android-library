/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.view.View
import android.view.ViewGroup

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

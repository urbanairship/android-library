/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children

/** Returns `true` for Right, `false` for Left, or `null` for any other key. */
internal fun KeyEvent.horizontalDirection(): Boolean? = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_RIGHT -> true
    KeyEvent.KEYCODE_DPAD_LEFT -> false
    else -> null
}

/**
 * Turns the scene's pager for a Left/Right key that focus has no use for, so a scene's own
 * controls, such as a close button drawn over the pager, can page it too.
 *
 * Only handles keys from outside every pager, since a pager already handles keys from inside it.
 * The key is left alone when focus can move that way within the scene, when a text field has
 * focus, and when the scene doesn't have exactly one outermost pager showing.
 *
 * @param event The key event.
 * @return `true` if the pager handled the key.
 */
internal fun ViewGroup.dispatchScenePagerKey(event: KeyEvent): Boolean {
    val isRight = event.horizontalDirection() ?: return false
    val focused = findFocus()
    if (focused != null && hasPagerBetween(focused)) return false

    val pager = outermostPagers().singleOrNull() ?: return false

    if (focused != null && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
        if (focused.onCheckIsTextEditor()) return false
        val direction = if (isRight) View.FOCUS_RIGHT else View.FOCUS_LEFT
        if (FocusFinder.getInstance().findNextFocus(this, focused, direction) != null) return false
    }

    return pager.dispatchOutsideKey(event)
}

private fun ViewGroup.hasPagerBetween(view: View): Boolean {
    var parent = view.parent
    while (parent != null && parent !== this) {
        if (parent is PagerView) return true
        parent = parent.parent
    }
    return false
}

private fun ViewGroup.outermostPagers(): List<PagerView> = children
    .filter { it.isShown }
    .flatMap { child ->
        when (child) {
            is PagerView -> sequenceOf(child)
            is ViewGroup -> child.outermostPagers().asSequence()
            else -> emptySequence()
        }
    }
    .toList()

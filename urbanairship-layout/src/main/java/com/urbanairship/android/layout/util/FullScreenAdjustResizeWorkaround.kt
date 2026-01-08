package com.urbanairship.android.layout.util

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout

/**
 * Allows a view to be resized when the keyboard is shown, even when the activity is in fullscreen.
 *
 * Workaround for this "won't fix" bug: https://issuetracker.google.com/issues/36911528
 */
internal class FullScreenAdjustResizeWorkaround private constructor(activity: Activity) {

    private val child: View
    private var previousUsableHeight: Int = 0
    private val layoutParams: FrameLayout.LayoutParams

    init {
        val content = activity.findViewById<FrameLayout>(android.R.id.content)
        child = content.getChildAt(0)
        child.viewTreeObserver.addOnGlobalLayoutListener {
            adjustResizeIfNeeded()
        }
        layoutParams = child.layoutParams as FrameLayout.LayoutParams
    }

    /**
     * Resizes the view to avoid being covered by the on-screen keyboard.
     *
     * Since Android doesn't provide a good "keyboard visible" API, we use a heuristic:
     * if more than 25% of the window is obscured from the bottom, we assume the keyboard
     * is visible and shrink the view accordingly. Smaller obstructions (like the nav bar)
     * are ignored.
     */
    private fun adjustResizeIfNeeded() {
        val visibleFrame = Rect().apply { child.getWindowVisibleDisplayFrame(this) }
        val usableHeight = visibleFrame.bottom - visibleFrame.top

        if (usableHeight != previousUsableHeight) {
            val fullWindowHeight = child.rootView.height
            val keyboardHeight = fullWindowHeight - visibleFrame.bottom

            if (keyboardHeight > (fullWindowHeight / 4)) {
                // The on-screen keyboard probably just became visible,
                // set height to fill from top of screen to top of keyboard
                layoutParams.height = fullWindowHeight - keyboardHeight
            } else {
                // The on-screen keyboard probably just dismissed,
                // set height to fill the entire screen
                layoutParams.height = fullWindowHeight
            }
            child.requestLayout()
            previousUsableHeight = usableHeight
        }
    }

    internal companion object {
        fun Activity.applyAdjustResizeWorkaround() {
            FullScreenAdjustResizeWorkaround(this)
        }
    }
}

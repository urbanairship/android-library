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

    private fun adjustResizeIfNeeded() {
        val usableHeight = getUsableHeight()
        if (usableHeight != previousUsableHeight) {
            val usableHeightMinusKeyboard = child.rootView.height
            val heightDelta = usableHeightMinusKeyboard - usableHeight
            if (heightDelta > (usableHeightMinusKeyboard / 4)) {
                // keyboard probably just became visible
                layoutParams.height = usableHeightMinusKeyboard - heightDelta
            } else {
                // keyboard probably just dismissed
                layoutParams.height = usableHeightMinusKeyboard
            }
            child.requestLayout()
            previousUsableHeight = usableHeight
        }
    }

    private fun getUsableHeight(): Int {
        val rect = Rect().apply { child.getWindowVisibleDisplayFrame(this) }
        return rect.bottom - rect.top
    }

    internal companion object {
        fun Activity.applyAdjustResizeWorkaround() {
            FullScreenAdjustResizeWorkaround(this)
        }
    }
}

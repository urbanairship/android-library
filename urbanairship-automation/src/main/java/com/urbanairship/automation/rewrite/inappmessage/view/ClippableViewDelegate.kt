/* Copyright Airship and Contributors */
package com.urbanairship.automation.rewrite.inappmessage.view

import android.graphics.Outline
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider
import androidx.annotation.MainThread

/**
 * View delegate that supports clipping a view to a border radius.
 */
internal class ClippableViewDelegate {

    /**
     * Clips the view to the border radius.
     *
     * @param borderRadius The border radius.
     */
    @MainThread
    fun setClipPathBorderRadius(view: View, borderRadius: Float) {
        val borderRadiusPixels = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            borderRadius,
            view.resources.displayMetrics
        )
        if (borderRadiusPixels == 0f) {
            view.clipToOutline = false
            view.outlineProvider = ViewOutlineProvider.BOUNDS
        } else {
            view.clipToOutline = true
            view.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(
                        0,
                        0,
                        view.right - view.left,
                        view.bottom - view.top, borderRadiusPixels
                    )
                }
            }
        }
    }
}

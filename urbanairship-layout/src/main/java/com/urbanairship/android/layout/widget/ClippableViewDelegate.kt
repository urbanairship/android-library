/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.graphics.Outline
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import androidx.annotation.Dimension
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
    fun setClipPathBorderRadius(view: View, @Dimension borderRadius: Float) {
        val radii = if (borderRadius == 0f) {
            null
        } else {
            FloatArray(8) { borderRadius }
        }
        setClipPathBorderRadii(view, radii)
    }

    /**
     * Clips the view to the border radius.
     *
     * @param borderRadius The border radius.
     */
    @MainThread
    fun setClipPathBorderRadii(view: View, radii: FloatArray?) {
        val radii = radii ?: kotlin.run {
            view.clipToOutline = false
            view.outlineProvider = ViewOutlineProvider.BOUNDS
            return
        }

        view.clipToOutline = true
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {

                val rect = Rect(
                    0,
                    0,
                    (view.right - view.left),
                    (view.bottom - view.top))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val path = Path().apply {
                        addRoundRect(RectF(rect), radii, Path.Direction.CW)
                    }

                    outline.setPath(path)
                } else {
                    outline.setRoundRect(rect, radii.max())
                }
            }
        }
    }
}

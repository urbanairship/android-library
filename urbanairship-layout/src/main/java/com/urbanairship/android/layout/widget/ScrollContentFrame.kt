/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout

/**
 * The single child a scrolling host is allowed, holding both its scrolling content and any
 * `backgrounds` layer drawn as a real view.
 *
 * `ScrollView`/`NestedScrollView` host exactly one direct child, so a layer view can't sit beside
 * the content -- it goes in here with it, kept still against the viewport by
 * [VideoBackgroundChildren.applyParallax]'s pin rather than scrolling with its siblings.
 *
 * Exists for [drawChild]: a layer stacked above one drawn as a real view has to blend against
 * that view's live pixels, which means drawing it here, between the layer view and the content,
 * rather than before or after the whole child.
 */
internal class ScrollContentFrame(context: Context) : FrameLayout(context) {

    var videoBackgrounds: VideoBackgroundChildren? = null

    /** Draws the layers stacked above real layer view `n`, in the host's own space. */
    var drawLayersAfter: ((Canvas, Int) -> Unit)? = null

    init {
        // A layer is deliberately oversized for its own parallax travel, so it overhangs.
        clipChildren = false
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        val result = super.drawChild(canvas, child, drawingTime)
        val attachedIndex = videoBackgrounds?.indexOf(child) ?: -1
        if (attachedIndex >= 0) {
            drawLayersAfter?.invoke(canvas, attachedIndex)
        }
        return result
    }
}

/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.graphics.Canvas
import android.view.View

/**
 * The box a host's `backgrounds` layers fill: its own box, less its padding.
 *
 * A border stroke arrives as padding, so this is the box inside the stroke, and a layer stops
 * there rather than running under it. iOS pads before drawing the border; a layer on web is
 * absolutely positioned, which resolves `100%` against the padding box. Painted to the outer box
 * a layer hides the stroke completely, while a layer hosted as a real child is inset by that
 * padding whatever the painter does -- so the two draw the same layer a stroke width apart.
 */
internal val View.backgroundsBoxWidth: Int
    get() = (width - paddingLeft - paddingRight).coerceAtLeast(0)

/** @see backgroundsBoxWidth */
internal val View.backgroundsBoxHeight: Int
    get() = (height - paddingTop - paddingBottom).coerceAtLeast(0)

/**
 * Runs [paint] with [canvas] moved to the origin of this host's [backgroundsBoxWidth] box, handing
 * it that box's width and height, and restores the canvas afterwards.
 *
 * @param canvas the host's own canvas, positioned at the host's box.
 * @param paint draws into the content box, taking its width and height.
 */
internal inline fun View.paintBackgroundsBox(canvas: Canvas, paint: (Int, Int) -> Unit) {
    val boxWidth = backgroundsBoxWidth
    val boxHeight = backgroundsBoxHeight
    if (boxWidth <= 0 || boxHeight <= 0) return

    val saveCount = canvas.save()
    canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
    try {
        paint(boxWidth, boxHeight)
    } finally {
        canvas.restoreToCount(saveCount)
    }
}

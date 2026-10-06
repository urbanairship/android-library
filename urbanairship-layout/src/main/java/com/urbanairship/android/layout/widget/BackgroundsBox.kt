/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.graphics.Canvas
import android.graphics.Path
import android.view.View
import com.urbanairship.android.layout.R

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
    // Rounded, the box's corners would paint over the stroke's curve.
    if (borderRadii != null) clipInsideStroke(canvas)
    canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
    try {
        paint(boxWidth, boxHeight)
    } finally {
        canvas.restoreToCount(saveCount)
    }
}

/** The border's corner radii in pixels, as the view's outline clips to them, or null for square corners. */
internal val View.borderRadii: FloatArray?
    get() = getTag(R.id.ua_border_radii) as? FloatArray

/**
 * Clips [canvas] to the box inside this view's border stroke, which arrives as padding.
 *
 * Each corner's radius shrinks by the stroke beside it, as a CSS padding box does: a 20 radius
 * inside a 3 stroke clips at 17. With square corners it is the padding box.
 *
 * @param canvas the view's own canvas, positioned at the view's box.
 */
internal fun View.clipInsideStroke(canvas: Canvas) {
    val left = paddingLeft.toFloat()
    val top = paddingTop.toFloat()
    val right = (width - paddingRight).toFloat()
    val bottom = (height - paddingBottom).toFloat()
    val radii = borderRadii
    if (radii == null) {
        canvas.clipRect(left, top, right, bottom)
        return
    }
    val clip = getTag(R.id.ua_inside_stroke_clip) as? InsideStrokeClip
        ?: InsideStrokeClip().also { setTag(R.id.ua_inside_stroke_clip, it) }
    canvas.clipPath(clip.pathFor(left, top, right, bottom, width.toFloat(), height.toFloat(), radii))
}

/** The rounded box inside a view's stroke, rebuilt only when the box or the radii change. */
private class InsideStrokeClip {
    private val path = Path()
    private val box = FloatArray(6) { Float.NaN }
    private val outerRadii = FloatArray(8)
    private val innerRadii = FloatArray(8)

    fun pathFor(
        left: Float, top: Float, right: Float, bottom: Float,
        width: Float, height: Float, radii: FloatArray
    ): Path {
        if (box[0] == left && box[1] == top && box[2] == right && box[3] == bottom &&
            box[4] == width && box[5] == height && outerRadii.contentEquals(radii)
        ) {
            return path
        }
        box[0] = left; box[1] = top; box[2] = right; box[3] = bottom; box[4] = width; box[5] = height
        radii.copyInto(outerRadii)

        // Outline radii run top-left, top-right, bottom-right, bottom-left, each as an x then a y.
        val insets = floatArrayOf(
            left, top, width - right, top, width - right, height - bottom, left, height - bottom
        )
        for (i in 0 until 8) innerRadii[i] = (radii[i] - insets[i]).coerceAtLeast(0f)
        path.rewind()
        path.addRoundRect(left, top, right, bottom, innerRadii, Path.Direction.CW)
        return path
    }
}

/**
 * Runs [draw] with [canvas] clipped to the box inside this view's border stroke, as a web layout's
 * `overflow: clip` does, then restores the canvas.
 *
 * @param canvas the view's own canvas, positioned at the view's box.
 * @param draw what to draw inside the stroke: typically the backgrounds and the children.
 */
internal inline fun View.drawInsideStroke(canvas: Canvas, draw: () -> Unit) {
    val saveCount = canvas.save()
    clipInsideStroke(canvas)
    try {
        draw()
    } finally {
        canvas.restoreToCount(saveCount)
    }
}

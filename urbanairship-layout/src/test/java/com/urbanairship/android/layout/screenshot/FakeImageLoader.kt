/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.screenshot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.urbanairship.images.ImageLoader
import com.urbanairship.images.ImageRequestOptions

/**
 * Stands in for [com.urbanairship.images.AirshipGlideImageLoader], which needs a flying Airship and
 * the network. Substituting it also severs Glide from the layout module entirely: nothing here
 * reaches Glide except through `Airship.imageLoader`.
 *
 * Loading is synchronous so a screenshot can be taken the moment the view is attached, and the
 * drawable's intrinsic size is fixed so every sizing decision the renderer makes from it is stable.
 */
internal object FakeImageLoader : ImageLoader {

    /** Matches the placeholder the iOS harness generates, so the two sweeps are comparable. */
    const val WIDTH: Int = 1200
    const val HEIGHT: Int = 800

    override fun load(context: Context, imageView: ImageView, imageRequestOptions: ImageRequestOptions) {
        imageView.setImageDrawable(PlaceholderDrawable())
        imageRequestOptions.callback?.onImageLoaded(true)
    }

    /**
     * Deliberately not a flat colour: the off-centre disc and the border make cropping, scaling and
     * aspect-ratio regressions visible in a diff, which a solid fill would hide.
     *
     * Everything is drawn in fractions of [getBounds] rather than absolute pixels so the figure
     * survives a `FIT_XY` media fit, where the bounds are the view's size and not the intrinsic one.
     */
    private class PlaceholderDrawable : Drawable() {

        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(217, 230, 247)
        }

        private val figure = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(26, 64, 153)
        }

        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(26, 64, 153)
            style = Paint.Style.STROKE
        }

        override fun getIntrinsicWidth(): Int = WIDTH

        override fun getIntrinsicHeight(): Int = HEIGHT

        override fun draw(canvas: Canvas) {
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            if (w <= 0f || h <= 0f) {
                return
            }

            canvas.save()
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())

            canvas.drawRect(0f, 0f, w, h, fill)
            canvas.drawOval(RectF(w * 0.21f, h * 0.19f, w * 0.54f, h * 0.69f), figure)

            val stroke = w * (40f / WIDTH)
            border.strokeWidth = stroke
            canvas.drawRect(stroke / 2f, stroke / 2f, w - stroke / 2f, h - stroke / 2f, border)

            canvas.restore()
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Required by the Drawable contract", ReplaceWith("PixelFormat.OPAQUE"))
        override fun getOpacity(): Int = PixelFormat.OPAQUE
    }
}

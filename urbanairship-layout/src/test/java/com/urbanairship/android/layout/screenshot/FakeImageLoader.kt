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
import android.net.Uri
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

    /** Used when the URL isn't a `test-layout.internal` placeholder (see [TestLayoutSpec]).
     * Matches the placeholder the iOS harness generates, so the two sweeps are comparable. */
    const val WIDTH: Int = 1200
    const val HEIGHT: Int = 800

    override fun load(context: Context, imageView: ImageView, imageRequestOptions: ImageRequestOptions) {
        imageView.setImageDrawable(PlaceholderDrawable(TestLayoutSpec.parse(imageRequestOptions.url)))
        imageRequestOptions.callback?.onImageLoaded(true)
    }

    /**
     * Deliberately not a flat colour: the off-centre disc and the border make cropping, scaling and
     * aspect-ratio regressions visible in a diff, which a solid fill would hide.
     *
     * Everything is drawn in fractions of [getBounds] rather than absolute pixels so the figure
     * survives a `FIT_XY` media fit, where the bounds are the view's size and not the intrinsic one.
     */
    private class PlaceholderDrawable(private val spec: TestLayoutSpec?) : Drawable() {

        private val intrinsicWidth = spec?.width ?: WIDTH
        private val intrinsicHeight = spec?.height ?: HEIGHT
        private val figureColor = spec?.color ?: Color.rgb(26, 64, 153)

        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec?.let { withAlpha(it.color, 90) } ?: Color.rgb(217, 230, 247)
        }

        private val figure = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = figureColor
        }

        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = figureColor
            style = Paint.Style.STROKE
        }

        override fun getIntrinsicWidth(): Int = intrinsicWidth

        override fun getIntrinsicHeight(): Int = intrinsicHeight

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

            val strokeFraction = (spec?.border ?: 40f) / intrinsicWidth
            val stroke = w * strokeFraction
            if (stroke > 0f) {
                border.strokeWidth = stroke
                canvas.drawRect(stroke / 2f, stroke / 2f, w - stroke / 2f, h - stroke / 2f, border)
            }

            canvas.restore()
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Required by the Drawable contract", ReplaceWith("PixelFormat.OPAQUE"))
        override fun getOpacity(): Int = PixelFormat.OPAQUE
    }
}

private fun withAlpha(color: Int, alpha: Int): Int =
    Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

/**
 * The shared `test-layout.internal` placeholder convention (also implemented in the devapp and on
 * iOS/web): `https://test-layout.internal/{width}/{height}/{color}/{border}`, dp/points, unscaled.
 */
private data class TestLayoutSpec(val width: Int, val height: Int, val color: Int, val border: Float) {
    companion object {
        private const val HOST = "test-layout.internal"

        private val namedColors = mapOf(
            "red" to Color.parseColor("#F44336"),
            "orange" to Color.parseColor("#FF9800"),
            "yellow" to Color.parseColor("#FFEB3B"),
            "green" to Color.parseColor("#4CAF50"),
            "teal" to Color.parseColor("#009688"),
            "blue" to Color.parseColor("#2196F3"),
            "purple" to Color.parseColor("#9C27B0"),
            "pink" to Color.parseColor("#E91E63"),
            "gray" to Color.parseColor("#9E9E9E"),
            "grey" to Color.parseColor("#9E9E9E"),
        )

        fun parse(url: String): TestLayoutSpec? {
            val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
            if (uri.host != HOST) return null

            val segments = uri.pathSegments
            if (segments.size < 4) return null
            val width = segments[0].toIntOrNull() ?: return null
            val height = segments[1].toIntOrNull() ?: return null
            val border = segments[3].toFloatOrNull() ?: return null
            val color = namedColors[segments[2].lowercase()] ?: Color.GRAY

            return TestLayoutSpec(width, height, color, border)
        }
    }
}

package com.urbanairship.devapp.thomas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.util.Size
import com.urbanairship.android.layout.util.CachedImage
import com.urbanairship.android.layout.util.ImageCache
import java.io.File
import java.io.FileOutputStream

/**
 * Resolves the shared `test-layout.internal` placeholder convention (also implemented on iOS
 * and web) into a locally synthesized bitmap, so a scene authored against a specific aspect
 * ratio renders the same way whether it's opened by hand in the devapp or captured by a UI
 * test -- no network, no third-party photo service.
 *
 * URL shape: `https://test-layout.internal/{width}/{height}/{color}/{border}`, where
 * `width`/`height`/`border` are dp (unscaled) and `color` is one of [namedColors].
 */
internal object TestLayoutPlaceholder {
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

    /** An [ImageCache] that resolves `test-layout.internal` URLs, deferring on everything else. */
    fun imageCache(context: Context): ImageCache = object : ImageCache {
        override fun get(url: String): CachedImage? = resolve(context, url)
        override fun tryAddChild(cache: ImageCache): String? = null
        override fun removeChild(id: String) {}
    }

    private fun resolve(context: Context, url: String): CachedImage? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        if (uri.host != HOST) return null

        val segments = uri.pathSegments
        if (segments.size < 4) return null
        val width = segments[0].toIntOrNull() ?: return null
        val height = segments[1].toIntOrNull() ?: return null
        val color = segments[2]
        val border = segments[3].toFloatOrNull() ?: return null

        val file = renderedImageFile(context, width, height, color, border) ?: return null
        return CachedImage(path = file.path, size = Size(width, height))
    }

    /** Writes (or reuses) a PNG at exactly [width]x[height], keyed by its own parameters so
     * repeated requests for the same placeholder in one run don't re-render it. */
    private fun renderedImageFile(
        context: Context,
        width: Int,
        height: Int,
        color: String,
        border: Float
    ): File? {
        val file = File(context.cacheDir, "test-layout-${width}x${height}-$color-${border.toInt()}.png")
        if (file.exists()) {
            return file
        }

        val fill = namedColors[color.lowercase()] ?: Color.GRAY
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.style = Paint.Style.FILL
        paint.color = withAlpha(fill, 90)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Off-center, so a crop's origin is visible in a screenshot, not just its aspect ratio.
        val markerSide = minOf(width, height) * 0.5f
        paint.color = fill
        canvas.drawOval(
            RectF(width * 0.15f, height * 0.15f, width * 0.15f + markerSide, height * 0.15f + markerSide),
            paint
        )

        if (border > 0) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = border
            paint.color = fill
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }

        return runCatching {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            file
        }.getOrNull()
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}

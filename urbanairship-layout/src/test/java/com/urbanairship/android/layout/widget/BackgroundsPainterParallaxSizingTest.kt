/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.Airship
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.property.ParallaxBackground
import com.urbanairship.images.ImageLoader
import com.urbanairship.images.ImageRequestOptions
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Ryan's report: "parallax sizing is fucked up." A parallaxing layer is drawn at exactly the
 * host's box size, then translated by `-factor * offsetPx` (see [BackgroundsPainter.drawLayer]
 * before this fix) -- so a box translated away from `(0, 0)` leaves a blank strip at the box's
 * trailing edge, since nothing was ever drawn to fill it. This isn't specific to `PagerView`'s
 * video-attach split; every parallaxing layer (color, gradient, image) has always had this shape,
 * since there was never any oversizing anywhere in this feature's history.
 *
 * Fixed by [BackgroundsPainter.parallaxOversize]: a parallaxing layer draws oversized by the box's
 * own size on the axis it moves along, centered at `offsetPx == 0`, and
 * [BackgroundsPainter.parallaxTranslation] clamps its translate to that same margin -- so the
 * clamped range can never outrun the oversized content. The clamp also matters on its own for
 * `ScrollLayout`, whose `offsetPx` is raw, unbounded `scrollY` (see the class doc) -- without it,
 * a long scroll would translate a parallaxing layer arbitrarily far past its own content.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
public class BackgroundsPainterParallaxSizingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val viewEnvironment: ViewEnvironment = mockk(relaxed = true) {
        every { imageCache() } returns null
    }

    @After
    public fun tearDown() {
        unmockkStatic(Airship::class)
        unmockkObject(Airship)
    }

    /**
     * Ryan's second real-device report, on this fix's own first pass: dragged past the edge of a
     * real photo, the box showed a solid, flat-colored bar instead of more of the photo -- the
     * margin was painted by clamping the small, un-oversized crop's own edge pixel outward
     * (`Shader.TileMode.CLAMP`) rather than laying the same crop out across the larger box, so the
     * whole margin repeated one edge pixel indefinitely no matter how far into it a drag went.
     *
     * A real photo's edge is rarely perfectly flat, so that bug is easy to miss with a solid-color
     * test double -- this drags to two different points within the *same* margin and asserts the
     * revealed pixel actually differs between them. A flat clamp can't: by construction, every
     * point in the margin reads back the one pixel it was smeared from.
     */
    @Test
    public fun draggingFurtherIntoTheMarginRevealsDifferentContentNotAConstantSmear() {
        mockkObject(Airship)
        mockkStatic(Airship::class)
        every { Airship.imageLoader } returns HorizontalGradientImageLoader

        val painter = BackgroundsPainter(context, viewEnvironment) {}

        fun leftEdgePixelAt(offsetPx: Float): Int {
            painter.setParallaxBackgrounds(maxFactorHorizontalImage())
            painter.offsetPx = offsetPx
            val bitmap = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
            painter.draw(Canvas(bitmap), 100, 50)
            return bitmap.getPixel(0, bitmap.height / 2)
        }

        val halfDragged = leftEdgePixelAt(50f)
        val fullyDragged = leftEdgePixelAt(100f)

        assertNotEquals(
            "dragging from half to fully into the margin should reveal a different part of the " +
                "gradient -- got the same pixel #%08X both times, meaning the margin is a constant " +
                "smear again rather than real image content"
                    .format(halfDragged),
            halfDragged,
            fullyDragged
        )
    }

    @Test
    public fun aFullyDraggedImageLayerStillCoversBothEdgesOfTheBox() {
        mockkObject(Airship)
        mockkStatic(Airship::class)
        every { Airship.imageLoader } returns SolidColorImageLoader(Color.RED)

        val painter = BackgroundsPainter(context, viewEnvironment) {}
        painter.setParallaxBackgrounds(maxFactorHorizontalImage())
        // The largest drag distance a Pager reports (see `BackgroundsPainter`'s class doc: the
        // offset from the *nearest* page, bounded to one page width) -- with `factor: 1`, this is
        // exactly the far end of the clamped range.
        painter.offsetPx = 100f

        val bitmap = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        painter.draw(Canvas(bitmap), 100, 50)

        assertFullyCovered(bitmap, Color.RED)
    }

    @Test
    public fun anUnboundedScrollOffsetClampsInsteadOfRunningPastTheContent() {
        mockkObject(Airship)
        mockkStatic(Airship::class)
        every { Airship.imageLoader } returns SolidColorImageLoader(Color.RED)

        val painter = BackgroundsPainter(context, viewEnvironment) {}
        painter.setParallaxBackgrounds(maxFactorHorizontalImage())
        // Stands in for `ScrollLayout`'s raw, unbounded `scrollY` -- far past one box-width.
        painter.offsetPx = 1_000_000f

        val bitmap = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        painter.draw(Canvas(bitmap), 100, 50)

        assertFullyCovered(bitmap, Color.RED)
    }

    @Test
    public fun aDraggedColorLayerStillCoversBothEdgesOfTheBox() {
        val backgrounds = ParallaxBackground.fromJsonList(
            JsonValue.parseString(
                """
                [
                  {
                    "type": "color",
                    "color": { "default": { "hex": "#FF0000", "alpha": 1 } },
                    "parallax": { "axis": "horizontal", "factor": 1 }
                  }
                ]
                """.trimIndent()
            ).optList()
        )

        val painter = BackgroundsPainter(context, viewEnvironment) {}
        painter.setParallaxBackgrounds(backgrounds)
        painter.offsetPx = 100f

        val bitmap = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        painter.draw(Canvas(bitmap), 100, 50)

        assertFullyCovered(bitmap, Color.RED)
    }

    @Test
    public fun aLayerWithNoParallaxIsUnaffectedByOffsetPx() {
        mockkObject(Airship)
        mockkStatic(Airship::class)
        every { Airship.imageLoader } returns SolidColorImageLoader(Color.RED)

        val backgrounds = ParallaxBackground.fromJsonList(
            JsonValue.parseString(
                """
                [
                  {
                    "type": "media", "media_type": "image", "url": "https://example/photo.jpg",
                    "media_fit": "fit_crop"
                  }
                ]
                """.trimIndent()
            ).optList()
        )

        val painter = BackgroundsPainter(context, viewEnvironment) {}
        painter.setParallaxBackgrounds(backgrounds)
        painter.offsetPx = 100f

        val bitmap = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        painter.draw(Canvas(bitmap), 100, 50)

        assertFullyCovered(bitmap, Color.RED)
    }

    private fun maxFactorHorizontalImage(): List<ParallaxBackground> = ParallaxBackground.fromJsonList(
        JsonValue.parseString(
            """
            [
              {
                "type": "media", "media_type": "image", "url": "https://example/photo.jpg",
                "media_fit": "fit_crop",
                "parallax": { "axis": "horizontal", "factor": 1 }
              }
            ]
            """.trimIndent()
        ).optList()
    )

    private fun assertFullyCovered(bitmap: Bitmap, expected: Int) {
        val leftEdge = bitmap.getPixel(0, bitmap.height / 2)
        val rightEdge = bitmap.getPixel(bitmap.width - 1, bitmap.height / 2)
        assertEquals(
            "left edge should still be covered -- got #%08X".format(leftEdge),
            String.format("#%08X", expected),
            String.format("#%08X", leftEdge)
        )
        assertEquals(
            "right edge should still be covered -- got #%08X".format(rightEdge),
            String.format("#%08X", expected),
            String.format("#%08X", rightEdge)
        )
    }

    /**
     * A synchronous loader that hands back a solid-color drawable, for coverage assertions.
     *
     * Wide (1000x100), not a bare pixel or two: the 100x50 box here is itself wide enough that
     * `fit_crop` already spans a square (or narrower) image's full width at rest, with zero
     * horizontal slack -- no resolution, however high, gives that shape anything left to reveal
     * at the same zoom level. Wide source content is what a fully-dragged `factor: 1` horizontal
     * parallax (this box oversized to 3x its own width) actually has to draw from.
     */
    private class SolidColorImageLoader(private val color: Int) : ImageLoader {
        override fun load(context: Context, imageView: ImageView, imageRequestOptions: ImageRequestOptions) {
            val bitmap = Bitmap.createBitmap(1000, 100, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(color)
            imageView.setImageDrawable(android.graphics.drawable.BitmapDrawable(context.resources, bitmap))
            imageRequestOptions.callback?.onImageLoaded(true)
        }
    }

    /** A synchronous loader with a real left-to-right gradient, so a revealed pixel's value can
     * tell two different points in the margin apart -- see
     * [draggingFurtherIntoTheMarginRevealsDifferentContentNotAConstantSmear]. */
    private object HorizontalGradientImageLoader : ImageLoader {
        override fun load(context: Context, imageView: ImageView, imageRequestOptions: ImageRequestOptions) {
            val width = 1000
            val bitmap = Bitmap.createBitmap(width, 100, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                val red = x * 255 / width
                for (y in 0 until bitmap.height) {
                    bitmap.setPixel(x, y, Color.rgb(red, 0, 0))
                }
            }
            imageView.setImageDrawable(android.graphics.drawable.BitmapDrawable(context.resources, bitmap))
            imageRequestOptions.callback?.onImageLoaded(true)
        }
    }
}

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
 * Fixed by [BackgroundsPainter.parallaxMargins]: a parallaxing layer draws oversized by the box's
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

    /** A synchronous loader that hands back a solid-color drawable, for coverage assertions. */
    private class SolidColorImageLoader(private val color: Int) : ImageLoader {
        override fun load(context: Context, imageView: ImageView, imageRequestOptions: ImageRequestOptions) {
            val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(color)
            imageView.setImageDrawable(android.graphics.drawable.BitmapDrawable(context.resources, bitmap))
            imageRequestOptions.callback?.onImageLoaded(true)
        }
    }
}

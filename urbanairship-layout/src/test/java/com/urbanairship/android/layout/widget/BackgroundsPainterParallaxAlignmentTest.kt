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
import com.urbanairship.android.layout.property.Background
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
 * Ryan's real-device report on `_backgrounds-parallax-horizontal-scroll.yml`: "looks like the
 * image is huge when calculating parallax effect" -- a gradient/image layer rendering as a giant,
 * wrongly-positioned shape rather than the pattern its own stops/crop describe.
 *
 * Root cause, distinct from [BackgroundsPainterParallaxSizingTest]'s gap/coverage fix: a `Shader`'s
 * own coordinates and the rect a `Paint` carrying it fills are read in one shared coordinate
 * space, both anchored at that space's `(0, 0)` -- [BackgroundsPainter.drawGradient] built its
 * gradient/radial shader from `shaderWidth`/`shaderHeight` alone and painted it into a larger
 * `drawWidth`/`drawHeight` rect, so the *pattern* landed in that rect's top-left corner rather
 * than centered in it, offset by exactly one margin on each parallaxing axis. At the box's own
 * visible window (which sits at that same margin's offset into the oversized rect), this reads as
 * uniformly the pattern's tail-end/clamped color -- for a two-stop gradient, solid, not a sweep.
 * A `factor: 0` (pinned) layer shows this on *every* frame, not just mid-drag, since its own
 * translate is always zero.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
public class BackgroundsPainterParallaxAlignmentTest {

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
    public fun aPinnedGradientLayerSweepsTheSameAsAnUnparallaxedOne() {
        val painter = BackgroundsPainter(context, viewEnvironment) {}
        painter.setParallaxBackgrounds(twoStopGradient(parallaxJson = PINNED_HORIZONTAL_PARALLAX))
        val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        painter.draw(Canvas(bitmap), 300, 200)

        assertEquals(
            "left edge should be the gradient's own start color, not its clamped tail",
            Color.RED,
            bitmap.getPixel(0, 100)
        )
        assertEquals(Color.BLUE, bitmap.getPixel(299, 100))
    }

    /** `factor: 0` oversizes by `abs(factor) * range` -- nothing -- so a pinned layer is the
     * unparallaxed render exactly, not merely close to it. iOS (`oversize`) and web
     * (`contentSize`) both fall out to the same no-op at zero. */
    @Test
    public fun aPinnedImageLayerCropsExactlyLikeAnUnparallaxedOne() {
        mockkObject(Airship)
        mockkStatic(Airship::class)
        every { Airship.imageLoader } returns MarkerBandImageLoader(
            size = 1000, bandStart = 240, bandEnd = 260, background = Color.BLUE, band = Color.RED
        )

        val plain = BackgroundsPainter(context, viewEnvironment) {}
        plain.backgrounds = listOf(fitCropImageBackground())
        val plainBitmap = Bitmap.createBitmap(900, 600, Bitmap.Config.ARGB_8888)
        plain.draw(Canvas(plainBitmap), 900, 600)

        val pinned = BackgroundsPainter(context, viewEnvironment) {}
        pinned.setParallaxBackgrounds(
            fitCropImage(parallaxJson = """{ "axis": "horizontal", "factor": 0 }""")
        )
        val pinnedBitmap = Bitmap.createBitmap(900, 600, Bitmap.Config.ARGB_8888)
        pinned.draw(Canvas(pinnedBitmap), 900, 600)

        for (y in intArrayOf(0, 70, 300, 599)) {
            assertEquals(
                "row $y of a factor-0 layer should be the unparallaxed render exactly",
                plainBitmap.getPixel(450, y),
                pinnedBitmap.getPixel(450, y)
            )
        }
        // Sanity check the fixture itself: the unparallaxed render actually shows the marker at
        // the row above, so the loop is comparing real content, not two blank renders.
        assertEquals(Color.RED, plainBitmap.getPixel(450, 70))
    }

    /**
     * Ryan's real-device report, comparing the same scene across platforms: "the image still
     * clips on android but not ios/web... I tried different aspect ratios and still not clipping
     * on those platforms."
     *
     * A cropping fit always fills the box it is handed, so cropping into the *oversized* box --
     * what iOS's `ThomasParallaxBackgroundLayerView` and web's `object-fit: cover` on a
     * `contentSize`-oversized `<img>` both do -- can never leave the box short, whatever shape
     * the source image is. Cropping into the layer's own smaller box and sliding that within the
     * oversized one (what Android did) can: `cover` only leaves slack on whichever axis the
     * image has proportionally more of, so an image merely shaped unlike its box runs out
     * mid-travel at any resolution. This fixture is exactly that shape -- a square source in a
     * wide box, whose `cover` slack is all vertical while the parallax travels horizontally.
     */
    @Test
    public fun aParallaxingImageLayerFullyCoversItsBoxAtEveryPointInItsTravel() {
        mockkObject(Airship)
        mockkStatic(Airship::class)
        every { Airship.imageLoader } returns MarkerBandImageLoader(
            size = 1000, bandStart = 240, bandEnd = 260, background = Color.BLUE, band = Color.RED
        )

        val painter = BackgroundsPainter(context, viewEnvironment) {}
        painter.setParallaxBackgrounds(
            fitCropImage(parallaxJson = """{ "axis": "horizontal", "factor": 0.4 }""")
        )
        painter.offsetBoundsPx = -900f..900f

        for (offsetPx in floatArrayOf(0f, 450f, 900f, -900f)) {
            painter.offsetPx = offsetPx
            val bitmap = Bitmap.createBitmap(900, 600, Bitmap.Config.ARGB_8888)
            painter.draw(Canvas(bitmap), 900, 600)

            for (x in intArrayOf(0, 1, 450, 898, 899)) {
                for (y in intArrayOf(0, 300, 599)) {
                    assertNotEquals(
                        "at offsetPx=$offsetPx, ($x, $y) is transparent -- the layer has been " +
                            "cropped into a box smaller than the one it's drawn into, so its " +
                            "own travel runs it off the edge",
                        0,
                        Color.alpha(bitmap.getPixel(x, y))
                    )
                }
            }
        }
    }

    private fun twoStopGradient(parallaxJson: String): List<ParallaxBackground> = ParallaxBackground.fromJsonList(
        JsonValue.parseString(
            """
            [
              {
                "type": "gradient",
                "gradient": {
                  "style": "linear", "angle": 90,
                  "stops": [
                    { "color": { "default": { "hex": "#FF0000", "alpha": 1 } }, "position": 0 },
                    { "color": { "default": { "hex": "#0000FF", "alpha": 1 } }, "position": 1 }
                  ]
                },
                "parallax": $parallaxJson
              }
            ]
            """.trimIndent()
        ).optList()
    )

    private fun fitCropImage(parallaxJson: String): List<ParallaxBackground> = ParallaxBackground.fromJsonList(
        JsonValue.parseString(
            """
            [
              {
                "type": "media", "media_type": "image", "url": "https://example/photo.jpg",
                "media_fit": "fit_crop", "position": { "horizontal": "center", "vertical": "center" },
                "parallax": $parallaxJson
              }
            ]
            """.trimIndent()
        ).optList()
    )

    private fun fitCropImageBackground(): Background = Background.fromJson(
        JsonValue.parseString(
            """
            {
              "type": "media", "media_type": "image", "url": "https://example/photo.jpg",
              "media_fit": "fit_crop", "position": { "horizontal": "center", "vertical": "center" }
            }
            """.trimIndent()
        ).requireMap()
    )

    private companion object {
        private const val PINNED_HORIZONTAL_PARALLAX = """{ "axis": "horizontal", "factor": 0 }"""
    }

    /** A synchronous loader handing back a square bitmap with a horizontal marker band. */
    private class MarkerBandImageLoader(
        private val size: Int,
        private val bandStart: Int,
        private val bandEnd: Int,
        private val background: Int,
        private val band: Int
    ) : ImageLoader {
        override fun load(context: Context, imageView: ImageView, imageRequestOptions: ImageRequestOptions) {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(background)
            for (y in bandStart until bandEnd) {
                for (x in 0 until size) {
                    bitmap.setPixel(x, y, band)
                }
            }
            imageView.setImageDrawable(android.graphics.drawable.BitmapDrawable(context.resources, bitmap))
            imageRequestOptions.callback?.onImageLoaded(true)
        }
    }
}

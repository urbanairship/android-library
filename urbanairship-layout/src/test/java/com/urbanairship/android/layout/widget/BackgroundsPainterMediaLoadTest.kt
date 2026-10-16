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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Root cause of "blend modes on Android do nothing" (real bug report, not a hypothesis): the
 * media layer's `CropImageView` is deliberately detached (see `BackgroundsPainter`'s class doc),
 * and the real image loader (`AirshipGlideImageLoader`) sizes its request from
 * `Target.getSize()`, which reads `view.width`/`view.height` synchronously and, if both are still
 * zero, falls back to `view.viewTreeObserver.addOnPreDrawListener(...)`. A detached view's
 * `ViewTreeObserver` never fires that callback -- there's no window traversal to fire it from --
 * so the load, and the `onImageLoaded` callback that triggers `invalidate()`, hung forever. The
 * color layer above it was blending against a never-painted, fully transparent destination, which
 * is mathematically indistinguishable from `blend_mode: normal` for every mode -- see
 * `BackgroundsPainterBlendModeTest.multiplyAgainstAFullyTransparentLayerBelowLooksLikeNormal`.
 *
 * Fixed by measuring and laying out the `CropImageView` *before* asking the loader to load, so
 * `getSize()`'s fast path is what fires, never the pre-draw listener. These tests assert the fix
 * directly (the view is actually sized when the loader is invoked) and end to end (a real image
 * layer with a blended color layer on top of it composites correctly, not as plain "normal").
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
public class BackgroundsPainterMediaLoadTest {

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
    public fun theImageViewIsAlreadySizedWhenTheLoaderIsAskedToLoad() {
        val loader = RecordingImageLoader()
        mockkObject(Airship)
        mockkStatic(Airship::class)
        every { Airship.imageLoader } returns loader

        val painter = BackgroundsPainter(context, viewEnvironment) {}
        painter.backgrounds = mediaOnly()

        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        painter.draw(Canvas(bitmap), 200, 100)

        assertEquals("the loader should be asked to load exactly once", 1, loader.loadCount)
        assertEquals(
            "view.width must already be resolved before Airship.imageLoader.load() is called " +
                "-- a detached view's ViewTreeObserver never fires the pre-draw listener the " +
                "real loader falls back to otherwise",
            200,
            loader.widthAtLoadTime
        )
        assertEquals(100, loader.heightAtLoadTime)
    }

    @Test
    public fun aRealPreDrawListenerNeverFiresOnADetachedView() {
        // The actual mechanism the bug hinges on, isolated from BackgroundsPainter entirely: a
        // freshly constructed, never-attached View's ViewTreeObserver does not fire onPreDraw,
        // because there is no window traversal to fire it from.
        val view = ImageView(context)
        var fired = false
        view.viewTreeObserver.addOnPreDrawListener {
            fired = true
            true
        }

        // Force whatever layout passes Robolectric would ever run for this (detached) view.
        view.measure(0, 0)
        view.layout(0, 0, 0, 0)
        view.invalidate()

        assertTrue("view must not be attached for this to be a fair test", !view.isAttachedToWindow)
        assertTrue("onPreDraw fired on a detached view -- the bug's premise no longer holds", !fired)
    }

    @Test
    public fun mediaBlendsCorrectlyOnceLoaded() {
        mockkObject(Airship)
        mockkStatic(Airship::class)
        every { Airship.imageLoader } returns SolidColorImageLoader(Color.RED)

        val backgrounds = Background.fromJsonList(
            JsonValue.parseString(
                """
                [
                  {
                    "type": "media", "media_type": "image", "url": "https://example/photo.jpg",
                    "media_fit": "fit_crop"
                  },
                  {
                    "type": "color",
                    "color": { "default": { "hex": "#00FF00", "alpha": 1 } },
                    "blend_mode": "multiply"
                  }
                ]
                """.trimIndent()
            ).optList()
        )

        val painter = BackgroundsPainter(context, viewEnvironment) {}
        painter.backgrounds = backgrounds

        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        painter.draw(Canvas(bitmap), 10, 10)

        // multiply(green, red) = black. If the photo never painted, this would read plain green
        // instead -- exactly the bug report.
        val pixel = bitmap.getPixel(5, 5)
        assertEquals(
            "expected the color layer to blend against the (now loaded) red photo -- got #%08X"
                .format(pixel),
            String.format("#%08X", Color.BLACK),
            String.format("#%08X", pixel)
        )
    }

    private fun mediaOnly(): List<Background> = Background.fromJsonList(
        JsonValue.parseString(
            """
            [
              { "type": "media", "media_type": "image", "url": "https://example/photo.jpg", "media_fit": "fit_crop" }
            ]
            """.trimIndent()
        ).optList()
    )

    /** Records the view's size at the exact moment `load()` is called, without touching Glide. */
    private class RecordingImageLoader : ImageLoader {
        var loadCount = 0
        var widthAtLoadTime = -1
        var heightAtLoadTime = -1

        override fun load(context: Context, imageView: ImageView, imageRequestOptions: ImageRequestOptions) {
            loadCount++
            widthAtLoadTime = imageView.width
            heightAtLoadTime = imageView.height
            imageRequestOptions.callback?.onImageLoaded(true)
        }
    }

    /** A synchronous loader that hands back a solid-color drawable, for blend-result assertions. */
    private class SolidColorImageLoader(private val color: Int) : ImageLoader {
        override fun load(context: Context, imageView: ImageView, imageRequestOptions: ImageRequestOptions) {
            // A real intrinsic size, unlike ColorDrawable (-1x-1) -- CropImageDelegate's matrix
            // math bails out early without one, same as it would for a still-loading drawable.
            val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(color)
            imageView.setImageDrawable(android.graphics.drawable.BitmapDrawable(context.resources, bitmap))
            imageRequestOptions.callback?.onImageLoaded(true)
        }
    }
}

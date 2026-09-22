/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Looper
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
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
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/**
 * Tests against *real* Glide (the library `AirshipGlideImageLoader` wraps -- that class itself is
 * `internal` to `urbanairship-core` and unreachable from this module's tests, so this drives the
 * same `Glide.with(view)...into(ImageView)` shape directly), not the synchronous fake from
 * `BackgroundsPainterMediaLoadTest`. The coordinator's concern was exactly that the fake couldn't
 * reproduce whatever is still broken on a real device. Points Glide at a local file (same as the
 * devapp's `TestLayoutPlaceholder` does), so this stays off the network while still exercising
 * Glide's real request/target/lifecycle machinery -- the thing actually in question is whether a
 * *detached* view breaks that machinery, not whether decoding a network image works.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
public class BackgroundsPainterRealGlideTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    public fun realGlideLoadsIntoADetachedCropImageView() {
        val file = File(context.cacheDir, "backgrounds-painter-test.png")
        FileOutputStream(file).use {
            Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        val imageView = CropImageView(context)
        imageView.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        // Measured/laid out before load, per the fix -- and never attached to a window, which is
        // exactly what's under test here.
        imageView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(50, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(50, android.view.View.MeasureSpec.EXACTLY)
        )
        imageView.layout(0, 0, 50, 50)
        assertTrue("view must not be attached for this to be a fair test", !imageView.isAttachedToWindow)

        var result: Boolean? = null
        val listener = object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>,
                isFirstResource: Boolean
            ): Boolean {
                result = false
                return false
            }

            override fun onResourceReady(
                resource: Drawable,
                model: Any,
                target: Target<Drawable>?,
                dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean {
                result = true
                return false
            }
        }

        Glide.with(imageView)
            .load(file.path)
            .addListener(listener)
            .into(imageView)

        // Glide's decode runs on its own executor even under Robolectric; drain the main looper
        // repeatedly so any posted callback actually lands, the same way SceneScreenshotTest drains
        // it for a real (attached) media view.
        var waited = 0
        while (result == null && waited < 2000) {
            shadowOf(Looper.getMainLooper()).idle()
            if (result != null) break
            Thread.sleep(10)
            waited += 10
        }

        assertTrue(
            "Glide never called back (onResourceReady/onLoadFailed) for a detached CropImageView " +
                "within 2s -- result=$result. If this hangs/fails, a detached view breaks Glide's " +
                "own request lifecycle, independent of the measure-before-load fix.",
            result != null
        )
        assertTrue("expected the load to succeed (it's a valid local PNG)", result == true)
        assertTrue("drawable should be set once loaded", imageView.drawable != null)
    }

    /**
     * Closes the gap the previous test leaves open: that one proves bare Glide tolerates a
     * detached view, but not that `BackgroundsPainter`'s actual code -- going through
     * `Airship.imageLoader`, `ImageRequestOptions`, `invalidate()`, the whole real shape -- ever
     * gets a usable pixel on screen. This wires a real-Glide-backed `ImageLoader` (the same shape
     * `AirshipGlideImageLoader` is, since that class itself is unreachable here) in as
     * `Airship.imageLoader` and runs `BackgroundsPainter` exactly as a host view would.
     */
    @Test
    public fun backgroundsPainterActuallyPaintsARealGlideLoadedImage() {
        val file = File(context.cacheDir, "backgrounds-painter-red.png")
        FileOutputStream(file).use {
            val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.RED)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        mockkObject(Airship)
        mockkStatic(Airship::class)
        every { Airship.imageLoader } returns RealGlideImageLoader
        val viewEnvironment: ViewEnvironment = mockk(relaxed = true) { every { imageCache() } returns null }

        try {
            val backgrounds = Background.fromJsonList(
                JsonValue.parseString(
                    """
                    [
                      { "type": "media", "media_type": "image", "url": "${file.path}", "media_fit": "fit_crop" },
                      {
                        "type": "color",
                        "color": { "default": { "hex": "#00FF00", "alpha": 1 } },
                        "blend_mode": "multiply"
                      }
                    ]
                    """.trimIndent()
                ).optList()
            )

            var invalidateCount = 0
            val painter = BackgroundsPainter(context, viewEnvironment) { invalidateCount++ }
            painter.backgrounds = backgrounds

            val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

            // First pass: kicks off the real Glide load. Not expected to show the photo yet.
            painter.draw(Canvas(bitmap), 10, 10)

            var waited = 0
            while (invalidateCount == 0 && waited < 2000) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
                waited += 10
            }
            assertTrue(
                "the loader's onImageLoaded callback (which calls invalidate()) never fired " +
                    "within 2s of a real Glide load against a detached CropImageView",
                invalidateCount > 0
            )

            // Second pass, standing in for the redraw invalidate() schedules: now the image
            // should actually be there for the color layer to blend against.
            painter.draw(Canvas(bitmap), 10, 10)

            val pixel = bitmap.getPixel(5, 5)
            assertEquals(
                "multiply(green, the now-loaded red photo) should read as black -- got #%08X " +
                    "instead, meaning the photo still never painted even after invalidate() fired"
                    .format(pixel),
                String.format("#%08X", Color.BLACK),
                String.format("#%08X", pixel)
            )
        } finally {
            unmockkStatic(Airship::class)
            unmockkObject(Airship)
        }
    }

    /** Same shape as `AirshipGlideImageLoader` (unreachable here -- internal to another module),
     * driving real Glide instead of a synchronous fake. */
    private object RealGlideImageLoader : ImageLoader {
        override fun load(context: Context, imageView: ImageView, imageRequestOptions: ImageRequestOptions) {
            val listener = object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    imageRequestOptions.callback?.onImageLoaded(false)
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    imageRequestOptions.callback?.onImageLoaded(true)
                    return false
                }
            }

            Glide.with(imageView)
                .load(imageRequestOptions.url)
                .addListener(listener)
                .into(imageView)
        }
    }
}

/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.property.Background
import com.urbanairship.json.JsonValue
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * `PagerView` draws a video `backgrounds` layer as a real attached child rather than through
 * [BackgroundsPainter]'s own canvas painting (video needs a real view to play at all), calling
 * [BackgroundsPainter.drawLayersBefore] and [BackgroundsPainter.drawLayersAfter] around that
 * child's own draw. These tests stand in for that child with a plain `canvas.drawColor` between
 * the two calls, the same way `PagerView.drawChild` composites the video's real pixels into that
 * same [Canvas] before calling [BackgroundsPainter.drawLayersAfter].
 *
 * A layer stacked after the attached view needs to blend against those already-composited
 * pixels, not against an empty buffer -- see `BackgroundsPainterBlendModeTest
 * .multiplyAgainstAFullyTransparentLayerBelowLooksLikeNormal` for what blending against nothing
 * looks like (indistinguishable from `blend_mode: normal`).
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
public class BackgroundsPainterAttachedViewBlendTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val viewEnvironment: ViewEnvironment = mockk(relaxed = true)

    @Test
    public fun aLayerAfterTheAttachedViewBlendsAgainstItsRealPixels() {
        val backgrounds = Background.fromJsonList(
            JsonValue.parseString(
                """
                [
                  { "type": "media", "media_type": "video", "url": "https://example/video.mp4", "media_fit": "fit_crop" },
                  {
                    "type": "color",
                    "color": { "default": { "hex": "#00FF00", "alpha": 1 } },
                    "blend_mode": "multiply"
                  }
                ]
                """.trimIndent()
            ).optList()
        ) as List<Background>

        val painter = BackgroundsPainter(context, viewEnvironment) {}
        painter.backgrounds = backgrounds
        // Same set PagerView builds from `videoBackgroundModels` -- the video layer is drawn as a
        // real attached child, never by the painter's own canvas painting.
        painter.attachedViewBackgrounds = setOf(backgrounds[0])

        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Nothing before the video layer, so this is a no-op here -- included to match the real
        // draw order (`PagerView.dispatchDraw`) exactly.
        painter.drawLayersBefore(canvas, 10, 10)

        // Stands in for the attached video child compositing its real (red) frame into this same
        // canvas via `PagerView.drawChild`, before `drawLayersAfter` runs.
        canvas.drawRect(0f, 0f, 10f, 10f, Paint().apply { color = Color.RED })

        painter.drawLayersAfter(canvas, 10, 10)

        // multiply(green, red) = black. Blending against nothing (the bug) reads back plain
        // green instead, since an opaque source composited over a blank layer looks like
        // `blend_mode: normal` regardless of which mode was requested.
        val pixel = bitmap.getPixel(5, 5)
        assertEquals(
            "expected the color layer to blend against the attached view's real pixels -- got #%08X"
                .format(pixel),
            String.format("#%08X", Color.BLACK),
            String.format("#%08X", pixel)
        )
    }
}

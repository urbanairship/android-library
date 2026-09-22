/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.property.Background
import com.urbanairship.json.JsonValue
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pixel-level proof that `blend_mode` actually changes what's drawn, not just that a `BlendMode`/
 * `PorterDuffXfermode` value was set somewhere. Renders a red layer with a green layer on top of
 * it through a real (Roborazzi/Robolectric NATIVE) Skia canvas and reads the composited pixel
 * back, at both a pre-API-29 and a post-API-29 SDK level -- the fallback split
 * `BackgroundsPainter.applyBlendMode` makes is exactly what's under suspicion.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
public class BackgroundsPainterBlendModeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val viewEnvironment: ViewEnvironment = mockk(relaxed = true)

    // ---- API 28 (this module's Robolectric default, and a plausible real-device floor) --------

    @Test
    @Config(sdk = [28])
    public fun normalDoesNotBlendAtApi28() {
        assertBlendResult(blendMode = "normal", expected = Color.GREEN)
    }

    @Test
    @Config(sdk = [28])
    public fun multiplyBlendsViaLegacyPorterDuffAtApi28() {
        // multiply(green, red) per channel = (0*255, 255*0, 0*0) = black -- nothing like plain
        // green, which is what it would read as if the mode weren't really being applied.
        assertBlendResult(blendMode = "multiply", expected = Color.BLACK)
    }

    @Test
    @Config(sdk = [28])
    public fun screenBlendsViaLegacyPorterDuffAtApi28() {
        // screen(green, red) per channel = (255+0-0, 0+255-0, 0+0-0) = yellow.
        assertBlendResult(blendMode = "screen", expected = Color.YELLOW)
    }

    @Test
    @Config(sdk = [28])
    public fun differenceHasNoLegacyFallbackAndDrawsAsNormalAtApi28() {
        // Deliberate, documented gap: no Porter-Duff equivalent below API 29.
        assertBlendResult(blendMode = "difference", expected = Color.GREEN)
    }

    // ---- API 30 (real android.graphics.BlendMode, no fallback involved) -----------------------

    @Test
    @Config(sdk = [30])
    public fun normalDoesNotBlendAtApi30() {
        assertBlendResult(blendMode = "normal", expected = Color.GREEN)
    }

    @Test
    @Config(sdk = [30])
    public fun multiplyBlendsAtApi30() {
        assertBlendResult(blendMode = "multiply", expected = Color.BLACK)
    }

    @Test
    @Config(sdk = [30])
    public fun screenBlendsAtApi30() {
        assertBlendResult(blendMode = "screen", expected = Color.YELLOW)
    }

    @Test
    @Config(sdk = [30])
    public fun differenceBlendsAtApi30() {
        // difference(green, red) per channel = (|0-255|, |255-0|, |0-0|) = yellow. Modes with no
        // legacy fallback are the ones most likely to have never actually been exercised.
        assertBlendResult(blendMode = "difference", expected = Color.YELLOW)
    }

    // ---- Diagnostic: does blending against nothing (yet) explain "every row looks the same"? --

    @Test
    @Config(sdk = [30])
    public fun multiplyAgainstAFullyTransparentLayerBelowLooksLikeNormal() {
        // No bottom layer at all -- standing in for "the photo hasn't loaded yet". If this comes
        // back green (the plain source color, same as `normal`) rather than some blended result,
        // blending against nothing collapses to "normal" by definition, regardless of platform.
        val backgrounds = Background.fromJsonList(
            JsonValue.parseString(
                """
                [
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

        val pixel = bitmap.getPixel(5, 5)
        assertEquals(
            "multiply against nothing: expected plain green but got #%08X".format(pixel),
            String.format("#%08X", Color.GREEN),
            String.format("#%08X", pixel)
        )
    }

    private fun assertBlendResult(blendMode: String, expected: Int) {
        val backgrounds = Background.fromJsonList(
            JsonValue.parseString(
                """
                [
                  { "type": "color", "color": { "default": { "hex": "#FF0000", "alpha": 1 } } },
                  {
                    "type": "color",
                    "color": { "default": { "hex": "#00FF00", "alpha": 1 } },
                    "blend_mode": "$blendMode"
                  }
                ]
                """.trimIndent()
            ).optList()
        )

        val painter = BackgroundsPainter(context, viewEnvironment) {}
        painter.backgrounds = backgrounds

        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        painter.draw(Canvas(bitmap), 10, 10)

        val pixel = bitmap.getPixel(5, 5)
        assertEquals(
            "blend_mode=$blendMode: expected #%08X but got #%08X".format(expected, pixel),
            String.format("#%08X", expected),
            String.format("#%08X", pixel)
        )
    }
}

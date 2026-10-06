/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.View.MeasureSpec
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import com.urbanairship.android.layout.property.Color as ThomasColor

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
public class TextInputStrokeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    public fun testTheFillStopsInsideTheStroke() {
        val pixels = draw(fillInsideStroke = true)

        // The translucent stroke over nothing, rather than over the fill.
        assertEquals(STROKE_ALPHA.toFloat(), Color.alpha(pixels.getPixel(STROKE / 2, BOX / 2)).toFloat(), 1f)
        assertEquals(Color.YELLOW, pixels.getPixel(BOX / 2, BOX / 2))
    }

    @Test
    public fun testTheFillRunsUnderTheStrokeByDefault() {
        val pixels = draw(fillInsideStroke = false)

        assertEquals(255, Color.alpha(pixels.getPixel(STROKE / 2, BOX / 2)))
    }

    @Test
    public fun testASingleLineIsNotCutOffInAShortField() {
        val field = SingleLineFittingEditText(context, fitsSingleLine = true).apply {
            textSize = 16f
            setPadding(PADDING, PADDING, PADDING, PADDING)
        }
        val height = field.lineHeight + PADDING
        field.measure(
            MeasureSpec.makeMeasureSpec(BOX, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )

        assertEquals(PADDING / 2, field.paddingTop)
        assertEquals(PADDING / 2, field.paddingBottom)
        assertEquals(PADDING, field.paddingLeft)
    }

    @Test
    public fun testATallFieldKeepsItsPadding() {
        val field = SingleLineFittingEditText(context, fitsSingleLine = true).apply {
            textSize = 16f
            setPadding(PADDING, PADDING, PADDING, PADDING)
        }
        field.measure(
            MeasureSpec.makeMeasureSpec(BOX, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(BOX, MeasureSpec.EXACTLY)
        )

        assertEquals(PADDING, field.paddingTop)
    }

    private fun draw(fillInsideStroke: Boolean): Bitmap {
        val view = View(context)
        LayoutUtils.applyBorderAndBackground(
            view,
            null,
            Border(radius = BOX / 2, strokeWidth = STROKE, strokeColor = strokeColor()),
            thomasColor("#FFFF00", 1.0),
            fillInsideStroke
        )
        view.measure(
            MeasureSpec.makeMeasureSpec(BOX, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(BOX, MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, BOX, BOX)
        return Bitmap.createBitmap(BOX, BOX, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private fun strokeColor() = thomasColor("#FF0000", STROKE_ALPHA / 255.0)

    private fun thomasColor(hex: String, alpha: Double): ThomasColor = ThomasColor.fromJson(
        JsonValue.parseString("""{ "default": { "hex": "$hex", "alpha": $alpha } }""")
    )

    private companion object {
        private const val BOX = 100
        private const val STROKE = 10
        private const val STROKE_ALPHA = 128
        private const val PADDING = 10
    }
}

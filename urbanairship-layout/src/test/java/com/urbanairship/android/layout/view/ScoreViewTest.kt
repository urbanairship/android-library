/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.model.ScoreModel
import com.urbanairship.android.layout.property.ScoreStyle
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonListOf
import com.urbanairship.json.jsonMapOf
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
public class ScoreViewTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val rowWidth = 300
    private val rowHeight = 44
    private val spacing = 8

    @Test
    public fun testItemsAreSquare() {
        val items = scoreRow(start = 0, end = 4).children()

        items.forEach { assertEquals(it.height, it.width) }
        assertEquals(rowHeight, items.first().height)
    }

    @Test
    public fun testItemsArePackedAtTheStatedSpacing() {
        val items = scoreRow(start = 0, end = 4).children()

        items.zipWithNext().forEach { (left, right) ->
            assertEquals(spacing, right.left - left.right)
        }
    }

    @Test
    public fun testTheRowIsCentred() {
        val items = scoreRow(start = 0, end = 4).children()

        assertEquals(rowWidth - items.last().right, items.first().left)
    }

    @Test
    public fun testWrappedItemsGrowWithTheirText() {
        val small = wrappedScore(fontSize = 14).children().first()
        val large = wrappedScore(fontSize = 40).children().first()

        // The tappable minimum covers a small font; a large one has to be given room of its own.
        assertEquals(44, small.measuredWidth)
        assertEquals(small.measuredWidth, small.measuredHeight)
        assertEquals(large.measuredWidth, large.measuredHeight)
        assertTrue(
            "expected an item larger than ${small.measuredWidth}, was ${large.measuredWidth}",
            large.measuredWidth > small.measuredWidth
        )
    }

    /** The items of a wrapping score, which size themselves rather than sharing out a row. */
    private fun wrappedScore(fontSize: Int): WrappingViewGroup {
        val style = ScoreStyle.WrappingNumberRange.fromJson(
            jsonMapOf(
                "type" to "number_range",
                "start" to 0,
                "end" to 10,
                "spacing" to spacing,
                "wrapping" to jsonMapOf("max_items_per_line" to 6, "line_spacing" to 8),
                "bindings" to bindings(fontSize)
            )
        ) as ScoreStyle.WrappingNumberRange

        val model = mockk<ScoreModel>(relaxed = true)
        every { model.viewId } returns View.generateViewId()
        every { model.viewInfo.style } returns style

        val view = ScoreView(context, model)
        shadowOf(Looper.getMainLooper()).idle()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(rowWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        return view.getChildAt(0) as WrappingViewGroup
    }

    private fun WrappingViewGroup.children(): List<View> =
        (0 until childCount).map { getChildAt(it) }

    private fun scoreRow(start: Int, end: Int): ScoreView {
        val style = ScoreStyle.NumberRange.fromJson(
            jsonMapOf(
                "type" to "number_range",
                "start" to start,
                "end" to end,
                "spacing" to spacing,
                "bindings" to bindings()
            )
        )

        val model = mockk<ScoreModel>(relaxed = true)
        every { model.viewId } returns View.generateViewId()
        every { model.viewInfo.style } returns style

        val view = ScoreView(context, model)
        shadowOf(Looper.getMainLooper()).idle()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(rowWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(rowHeight, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        return view
    }

    /** A filled square either way round, which is the simplest thing a score item can be. */
    private fun bindings(fontSize: Int = 14): JsonValue {
        val textAppearance = jsonMapOf(
            "font_size" to fontSize,
            "alignment" to "center",
            "color" to jsonMapOf("default" to jsonMapOf("hex" to "#000000", "alpha" to 1.0))
        )

        fun binding(hex: String) = jsonMapOf(
            "text_appearance" to textAppearance,
            "shapes" to jsonListOf(
                jsonMapOf(
                    "type" to "rectangle",
                    "aspect_ratio" to 1.0,
                    "color" to jsonMapOf("default" to jsonMapOf("hex" to hex, "alpha" to 1.0))
                )
            )
        )

        return jsonMapOf(
            "selected" to binding("#5588ff"),
            "unselected" to binding("#ffffff")
        ).toJsonValue()
    }

    private fun ScoreView.children(): List<View> =
        (0 until childCount).map { getChildAt(it) }
}

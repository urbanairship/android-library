/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.model.ScoreModel
import com.urbanairship.android.layout.property.ScoreStyle
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/**
 * A score's items, which are squares packed at the spacing the style asks for.
 *
 * Items free to take a width of their own share the row out between them, and then the spacing
 * between one shape and the next is whatever is left over rather than what was asked for — eight
 * points became twenty-odd, and grew with the window.
 */
// Text is measured for real, since what an item is sized by is the number in it.
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
            JsonMap.newBuilder()
                .put("type", "number_range")
                .put("start", 0)
                .put("end", 10)
                .put("spacing", spacing)
                .put(
                    "wrapping",
                    JsonMap.newBuilder()
                        .put("max_items_per_line", 6)
                        .put("line_spacing", 8)
                        .build()
                )
                .put("bindings", bindings(fontSize))
                .build()
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
            JsonMap.newBuilder()
                .put("type", "number_range")
                .put("start", start)
                .put("end", end)
                .put("spacing", spacing)
                .put("bindings", bindings())
                .build()
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
        val textAppearance = JsonMap.newBuilder()
            .put("font_size", fontSize)
            .put("alignment", "center")
            .put(
                "color",
                JsonMap.newBuilder()
                    .put(
                        "default",
                        JsonMap.newBuilder().put("hex", "#000000").put("alpha", 1.0).build()
                    )
                    .build()
            )
            .build()

        fun binding(hex: String) = JsonMap.newBuilder()
            .put("text_appearance", textAppearance)
            .put(
                "shapes",
                JsonValue.wrap(
                    listOf(
                        JsonMap.newBuilder()
                            .put("type", "rectangle")
                            .put("aspect_ratio", 1.0)
                            .put(
                                "color",
                                JsonMap.newBuilder()
                                    .put(
                                        "default",
                                        JsonMap.newBuilder()
                                            .put("hex", hex)
                                            .put("alpha", 1.0)
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                )
            )
            .build()

        return JsonMap.newBuilder()
            .put("selected", binding("#5588ff"))
            .put("unselected", binding("#ffffff"))
            .build()
            .toJsonValue()
    }

    private fun ScoreView.children(): List<View> =
        (0 until childCount).map { getChildAt(it) }
}

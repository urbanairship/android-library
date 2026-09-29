/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.Airship
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.ui.LayoutViewModel
import com.urbanairship.images.ImageLoader
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The grid layout rules, measured at mdpi so a pixel is a point and the numbers match the spec's.
 *
 * Native graphics for real text measurement, which the auto-height rows depend on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "mdpi")
@RunWith(RobolectricTestRunner::class)
public class GridLayoutViewTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val mockEnv: ModelEnvironment = mockk(relaxed = true) {
        every { layoutState } returns LayoutState.EMPTY
        every { layoutEvents } returns emptyFlow()
        every { modelScope } returns testScope
    }
    private val viewEnv: ViewEnvironment = mockk(relaxed = true) {
        every { isIgnoringSafeAreas } returns false
        every { imageCache() } returns null
    }

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(Airship)
        every { Airship.imageLoader } returns mockk<ImageLoader>(relaxed = true)
    }

    @After
    public fun tearDown() {
        unmockkObject(Airship)
        Dispatchers.resetMain()
    }

    @Test
    public fun testAnExactFitCountsAsAColumn() {
        val grid = grid(
            config = """{"max_columns": 10, "min_column_width": {"type": "fixed", "points": 50}, "column_spacing": 10}""",
            items = List(3) { box() }
        )
        layout(grid, width = 170)

        assertEquals(listOf(0, 60, 120), cells(grid).map { it.left })
        assertEquals(listOf(50, 50, 50), cells(grid).map { it.width })

        layout(grid, width = 169)
        assertEquals(listOf(0, 90, 0), cells(grid).map { it.left })
    }

    @Test
    public fun testAFullRowFillsTheWidth() {
        val grid = grid(config = """{"max_columns": 3, "column_spacing": 6}""", items = List(3) { box() })
        layout(grid, width = 301)

        val last = cells(grid).last()
        assertEquals(301, last.right)
    }

    @Test
    public fun testTheInnerWidthLeavesOutTheBorder() {
        val grid = grid(
            config = """{"max_columns": 4, "min_column_width": {"type": "fixed", "points": 50}}""",
            items = List(4) { box() },
            extra = """"border": {"stroke_width": 10, "stroke_color": {"default": {"type": "hex", "hex": "#000000", "alpha": 1}}},"""
        )
        // 200 across, 180 inside the stroke: three 60-point columns, not four 50s.
        layout(grid, width = 200)
        assertEquals(listOf(60, 60, 60), cells(grid).take(3).map { it.width })
        assertEquals(10, cells(grid).first().left)
    }

    @Test
    public fun testAShortRowIsCentredByHalfAColumn() {
        val grid = grid(
            config = """{"max_columns": 3, "column_spacing": 6, "partial_row_alignment": "center"}""",
            items = List(5) { box() }
        )
        layout(grid, width = 300)

        // Two items in three columns start half a column (plus half a gap) in.
        assertEquals(listOf(51, 153), cells(grid).drop(3).map { it.left })
        assertEquals(96, cells(grid)[3].width)
    }

    @Test
    public fun testStartIsTheRightEdgeInRtl() {
        ApplicationProvider.getApplicationContext<Context>().applicationInfo.flags =
            ApplicationProvider.getApplicationContext<Context>().applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val grid = grid(config = """{"max_columns": 3}""", items = List(2) { box() })
        grid.layoutDirection = View.LAYOUT_DIRECTION_RTL
        layout(grid, width = 300)

        assertEquals(listOf(200, 100), cells(grid).map { it.left })
    }

    @Test
    public fun testMarginsComeOutOfTheCellBeforePercentagesResolve() {
        val grid = grid(
            config = """{"max_columns": 3, "column_spacing": 6, "cell_aspect_ratio": 1}""",
            items = listOf(
                box(size = """{"width": "100%", "height": "100%"}""", margin = """{"top": 10, "bottom": 10, "start": 10, "end": 10}"""),
                box(size = """{"width": 200, "height": 30}"""),
                box(size = """{"width": 48, "height": 48}""")
            )
        )
        layout(grid, width = 300)

        val items = items(grid)
        assertEquals(Rect(10, 10, 86, 86), items[0].boundsInCell())
        assertEquals("capped at the cell", 96, items[1].width)
        assertEquals("centred by default", Rect(24, 24, 72, 72), items[2].boundsInCell())
    }

    @Test
    public fun testARatioSetsEveryRowFromOneColumn() {
        val grid = grid(
            config = """{"max_columns": 3, "column_spacing": 6, "row_spacing": 4, "cell_aspect_ratio": 2}""",
            items = listOf(box(span = 2), box(), box())
        )
        layout(grid, width = 300)

        assertEquals(listOf(48, 48, 48), cells(grid).map { it.height })
        assertEquals("a span keeps the row height, not twice it", 198, cells(grid)[0].width)
        assertEquals(52, cells(grid)[2].top)
        assertEquals(100, grid.measuredHeight)
    }

    @Test
    public fun testAPercentHeightIsAShareOfARowWithABasis() {
        val grid = grid(
            config = """{"max_columns": 3}""",
            items = listOf(
                box(size = """{"width": "100%", "height": 60}"""),
                box(size = """{"width": "100%", "height": "100%"}"""),
                box(size = """{"width": "100%", "height": "50%"}""")
            )
        )
        layout(grid, width = 300)

        assertEquals(listOf(60, 60, 30), items(grid).map { it.height })
        assertEquals("centred in its cell", 15, items(grid)[2].top)
    }

    @Test
    public fun testARowOfFullHeightCardsComesOutOneHeight() {
        val grid = grid(
            config = """{"max_columns": 2}""",
            items = listOf(
                label("Short", size = """{"width": "100%", "height": "100%"}"""),
                label("A much longer card body that wraps across several lines in its cell",
                    size = """{"width": "100%", "height": "100%"}""")
            )
        )
        layout(grid, width = 200)

        val heights = items(grid).map { it.height }
        assertEquals(heights[0], heights[1])
        assertTrue(heights[0] > 30)
    }

    @Test
    public fun testAShareUnderTheWholeHugsInARowWithNoBasis() {
        val grid = grid(
            config = """{"max_columns": 2}""",
            items = listOf(
                label("fifty percent", size = """{"width": "100%", "height": "50%"}"""),
                label("fifty percent", size = """{"width": "100%", "height": "50%"}""")
            )
        )
        layout(grid, width = 300)

        val natural = label("fifty percent").let { json ->
            val view = build(json)
            view.measure(atMost(150), unspecified())
            view.measuredHeight
        }
        assertEquals(listOf(natural, natural), items(grid).map { it.height })
    }

    @Test
    public fun testRowsSizeIndependently() {
        val grid = grid(
            config = """{"max_columns": 2}""",
            items = listOf(
                box(size = """{"width": "100%", "height": 60}"""),
                box(size = """{"width": "100%", "height": 20}"""),
                box(size = """{"width": "100%", "height": 24}""")
            )
        )
        layout(grid, width = 200)

        assertEquals(listOf(60, 60, 24), cells(grid).map { it.height })
        assertEquals(84, grid.measuredHeight)
    }

    @Test
    public fun testRowAlignmentPlacesTheRowsInATallerGrid() {
        val grid = grid(
            config = """{"max_columns": 2, "row_alignment": "center", "cell_aspect_ratio": 1}""",
            items = List(2) { box() }
        )
        layout(grid, width = 200, height = 300)

        assertEquals(100, cells(grid).first().top)
    }

    @Test
    public fun testAHiddenItemClosesUpWithoutRecreatingLaterCells() {
        val grid = grid(config = """{"max_columns": 3, "cell_aspect_ratio": 1}""", items = List(4) { box() })
        layout(grid, width = 300)
        assertEquals(100, cells(grid)[3].top)
        val before = items(grid)

        before[0].visibility = View.GONE
        layout(grid, width = 300)

        assertEquals(before, items(grid))
        assertSame(before[3], items(grid)[3])
        assertEquals("the fourth item moves up a row", listOf(0, 100, 200), cells(grid).drop(1).map { it.left })
        assertEquals(0, cells(grid)[3].top)
        assertEquals(0, cells(grid)[0].width)
    }

    @Test
    public fun testAnUnboundedGridTakesItsMinimumColumns() {
        val grid = grid(
            config = """{"max_columns": 4, "min_column_width": {"type": "fixed", "points": 50}, "column_spacing": 10}""",
            items = List(6) { box() }
        )
        grid.measure(unspecified(), unspecified())

        assertEquals(4 * 50 + 3 * 10, grid.measuredWidth)
    }

    @Test
    public fun testTheConfigIsPickedByTheGridsWidth() {
        val grid = grid(
            config = """{"max_columns": 2, "cell_aspect_ratio": 1}""",
            selectors = """[{"grid_width": {"at_least": 480}, "config": {"max_columns": 6, "cell_aspect_ratio": 1}}]""",
            items = List(6) { box() }
        )
        layout(grid, width = 479)
        assertEquals(2, cells(grid).count { it.top == 0 })

        layout(grid, width = 480)
        assertEquals(6, cells(grid).count { it.top == 0 })
    }

    @Test
    public fun testASpanThatDoesNotFitStartsANewRow() {
        val grid = grid(
            config = """{"max_columns": 3, "cell_aspect_ratio": 1}""",
            items = listOf(box(span = 2), box(span = 2), box())
        )
        layout(grid, width = 300)

        assertEquals(listOf(0, 100, 100), cells(grid).map { it.top })
        assertEquals(listOf(0, 0, 200), cells(grid).map { it.left })
    }

    @Test
    public fun testAnAutoGridFillsTheStackLessItsMargins() {
        val stack = stack(
            direction = "vertical",
            items = listOf(
                """
                {"size": {"width": "auto", "height": "auto"}, "margin": {"start": 16, "end": 16},
                 "view": ${grid("""{"max_columns": 4, "column_spacing": 8, "cell_aspect_ratio": 1}""")}}
                """
            )
        )
        layout(stack, width = 300)

        val grid = (stack as ViewGroup).getChildAt(0)
        assertEquals("inset by one margin", 16, grid.left)
        assertEquals("300 less both margins", 268, grid.width)
    }

    @Test
    public fun testAnAutoGridBesideALabelTakesWhatTheLabelLeaves() {
        val stack = stack(
            direction = "horizontal",
            items = listOf(
                """
                {"size": {"width": "auto", "height": "auto"},
                 "view": ${grid("""{"max_columns": 4, "column_spacing": 8, "cell_aspect_ratio": 1}""")}}
                """,
                """{"size": {"width": "auto", "height": "auto"}, "view": ${label("beside")}}"""
            )
        )
        layout(stack, width = 300)

        val grid = (stack as ViewGroup).getChildAt(0)
        val beside = stack.getChildAt(1)
        assertTrue("the label is drawn whole: ${beside.width}", beside.width > 20)
        assertEquals("the grid takes the rest", 300 - beside.width, grid.width)
    }

    @Test
    public fun testALongLabelBesideAnAutoGridKeepsItsWholeLine() {
        val text = "A label wider than half the row"
        val stack = stack(
            direction = "horizontal",
            items = listOf(
                """
                {"size": {"width": "auto", "height": "auto"},
                 "view": ${grid("""{"max_columns": 4, "column_spacing": 8, "cell_aspect_ratio": 1}""")}}
                """,
                """{"size": {"width": "auto", "height": "auto"}, "view": ${label(text)}}"""
            )
        )
        layout(stack, width = 300)

        val whole = build(label(text)).apply { measure(unspecified(), unspecified()) }.measuredWidth
        assertTrue("the fixture needs a label over half the row: $whole", whole in 151..299)

        val grid = (stack as ViewGroup).getChildAt(0)
        val beside = stack.getChildAt(1)
        assertEquals("the label keeps its whole line", whole, beside.width)
        assertEquals("the grid takes the rest", 300 - whole, grid.width)
    }

    @Test
    public fun testAShrinkableAutoGridBesideAPercentSiblingTakesTheRest() {
        val image = """
            {"size": {"width": "100%", "height": 40},
             "view": {"type": "media", "media_type": "image", "media_fit": "center_inside",
                      "url": "https://test-layout.internal/tile.png"}}
        """.trimIndent()
        val stack = stack(
            direction = "horizontal",
            items = listOf(
                """
                {"size": {"width": "auto", "height": "auto"},
                 "view": {"type": "grid_layout", "config": {"default": {"max_columns": 2, "column_spacing": 8}},
                          "items": [$image, $image]}}
                """,
                """{"size": {"width": "30%", "height": "auto"}, "view": ${label("thirty")}}"""
            )
        )
        layout(stack, width = 300)

        val grid = (stack as ViewGroup).getChildAt(0)
        val label = stack.getChildAt(1)
        assertEquals("the label takes its 30%", 90, label.width)
        assertEquals("the grid takes the rest", 210, grid.width)
    }

    @Test
    public fun testAnAutoGridBesideAPercentSiblingTakesTheRest() {
        val stack = stack(
            direction = "horizontal",
            items = listOf(
                """
                {"size": {"width": "auto", "height": "auto"},
                 "view": ${grid("""{"max_columns": 2, "column_spacing": 8, "cell_aspect_ratio": 1}""")}}
                """,
                """{"size": {"width": "30%", "height": "auto"}, "view": ${label("thirty")}}"""
            )
        )
        layout(stack, width = 300)

        assertEquals("the label takes its 30%", 90, (stack as ViewGroup).getChildAt(1).width)
        assertEquals("the grid takes the rest", 210, stack.getChildAt(0).width)
    }

    @Test
    public fun testAnAutoGridBeforeAFixedSiblingLeavesItsRoom() {
        val stack = stack(
            direction = "horizontal",
            items = listOf(
                """
                {"size": {"width": "auto", "height": "auto"},
                 "view": ${grid("""{"max_columns": 2, "column_spacing": 8, "cell_aspect_ratio": 1}""")}}
                """,
                """{"size": {"width": 80, "height": "auto"}, "view": ${label("eighty")}}"""
            )
        )
        layout(stack, width = 300)

        val fixed = (stack as ViewGroup).getChildAt(1)
        assertEquals(220, stack.getChildAt(0).width)
        assertEquals("the fixed sibling stays inside the row", 300, fixed.right)
    }

    @Test
    public fun testAFullHeightItemsContentGrowsARowWithABasis() {
        val longText = "Now much longer text that needs several more lines in this same cell"
        val grid = grid(
            config = """{"max_columns": 2}""",
            items = listOf(
                label("short", size = """{"width": "100%", "height": "auto"}"""),
                label(longText, size = """{"width": "100%", "height": "100%"}""")
            )
        )
        layout(grid, width = 200)

        val (short, long) = items(grid)
        assertTrue("the long label wraps onto several lines: ${long.height} vs ${short.height}", long.height > 2 * short.height)
        assertEquals("and sets the row", long.height, cells(grid)[0].height)
    }

    private fun View.boundsInCell() = Rect(left, top, right, bottom)

    @Test
    public fun testAFullHeightLabelGrowsItsRowWhenItsTextGrows() {
        val grid = grid(
            config = """{"max_columns": 2}""",
            items = listOf(
                label("short", size = """{"width": "100%", "height": "auto"}"""),
                label("Two short lines of text in this cell.", size = """{"width": "100%", "height": "100%"}""")
            )
        )
        layout(grid, width = 200)
        val before = cells(grid)[0].height

        val long = items(grid)[1] as android.widget.TextView
        long.text = "Now much longer text that needs several more lines in this same cell, so the row has to grow taller"
        assertTrue("the new text asks for a layout", grid.isLayoutRequested)

        layout(grid, width = 200)
        assertTrue("the row grows: $before -> ${cells(grid)[0].height}", cells(grid)[0].height > before)
        assertEquals("to the whole of the new text", long.layout.height + long.totalPaddingTop + long.totalPaddingBottom, long.height)

        long.text = "Two short lines of text in this cell."
        layout(grid, width = 200)
        assertEquals("and shrinks back", before, cells(grid)[0].height)
    }

    private fun grid(config: String): String = """
        {"type": "grid_layout", "config": {"default": $config},
         "items": [${List(4) { box() }.joinToString()}]}
    """.trimIndent()

    private fun stack(direction: String, items: List<String>): View = build(
        """{"type": "linear_layout", "direction": "$direction", "items": [${items.joinToString()}]}"""
    ).also { view ->
        Robolectric.buildActivity(Activity::class.java).setup().get()
            .setContentView(FrameLayout(view.context).apply { addView(view) })
    }

    private fun cells(grid: ViewGroup): List<View> = (0 until grid.childCount).map { grid.getChildAt(it) }

    private fun items(grid: ViewGroup): List<View> = cells(grid).map { (it as ViewGroup).getChildAt(0) }

    private fun layout(grid: View, width: Int, height: Int? = null) {
        grid.measure(exactly(width), height?.let(::exactly) ?: unspecified())
        grid.layout(0, 0, grid.measuredWidth, grid.measuredHeight)
    }

    private fun grid(config: String, items: List<String>, selectors: String? = null, extra: String = ""): ViewGroup =
        build(
            """
            {
              "type": "grid_layout",
              $extra
              "config": {"default": $config ${selectors?.let { """, "selectors": $it""" } ?: ""}},
              "items": [${items.joinToString()}]
            }
            """.trimIndent()
        ).also { view ->
            Robolectric.buildActivity(Activity::class.java).setup().get()
                .setContentView(FrameLayout(view.context).apply { addView(view) })
        } as ViewGroup

    private fun build(json: String): View {
        val info = ViewInfo.viewInfoFromJson(JsonValue.parseString(json).requireMap())
        return LayoutViewModel().getOrCreateModel(info, mockEnv)
            .createView(ApplicationProvider.getApplicationContext(), viewEnv, null)
    }

    private fun box(
        size: String = """{"width": "100%", "height": "100%"}""",
        span: Int = 1,
        margin: String? = null
    ) = """
        {
          "size": $size,
          "column_span": $span,
          ${margin?.let { """"margin": $it,""" } ?: ""}
          "view": {"type": "empty_view"}
        }
    """.trimIndent()

    private fun label(text: String, size: String? = null): String {
        val view = """
            {"type": "label", "text": "$text",
             "text_appearance": {"font_size": 14, "color": {"default": {"type": "hex", "hex": "#000000", "alpha": 1}}}}
        """.trimIndent()
        return if (size == null) view else """{"size": $size, "view": $view}"""
    }

    private fun exactly(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
    private fun atMost(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.AT_MOST)
    private fun unspecified() = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
}

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
import com.urbanairship.android.layout.model.GridLayoutModel
import com.urbanairship.android.layout.environment.ThomasState
import com.urbanairship.android.layout.environment.ThomasCapabilities
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.ui.LayoutViewModel
import com.urbanairship.android.layout.util.UI_TEST_RANDOM_SEED
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import androidx.core.view.isVisible
import java.time.Duration
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private val mockEnv: ModelEnvironment = mockEnv(LayoutState.EMPTY)

    /** A stand-in environment that keeps the layout state the factory builds, a grid's state included. */
    private fun mockEnv(state: LayoutState): ModelEnvironment = mockk(relaxed = true) {
        every { layoutState } returns state
        every { layoutEvents } returns emptyFlow()
        every { modelScope } returns testScope
        every { withState(any()) } answers { mockEnv(firstArg()) }
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
            config = """{"columns": {"type": "fit", "max": 10, "min_width": {"type": "fixed", "size": 50}}, "column_spacing": {"type": "fixed", "space": 10}}""",
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
        val grid = grid(config = """{"columns": {"type": "fixed", "count": 3}, "column_spacing": {"type": "fixed", "space": 6}}""", items = List(3) { box() })
        layout(grid, width = 301)

        val last = cells(grid).last()
        assertEquals(301, last.right)
    }

    @Test
    public fun testTheInnerWidthLeavesOutTheBorder() {
        val grid = grid(
            config = """{"columns": {"type": "fit", "max": 4, "min_width": {"type": "fixed", "size": 50}}}""",
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
            config = """{"columns": {"type": "fixed", "count": 3}, "column_spacing": {"type": "fixed", "space": 6}, "partial_row_position": "center"}""",
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
        val grid = grid(config = """{"columns": {"type": "fixed", "count": 3}}""", items = List(2) { box() })
        grid.layoutDirection = View.LAYOUT_DIRECTION_RTL
        layout(grid, width = 300)

        assertEquals(listOf(200, 100), cells(grid).map { it.left })
    }

    @Test
    public fun testMarginsComeOutOfTheCellBeforePercentagesResolve() {
        val grid = grid(
            config = """{"columns": {"type": "fixed", "count": 3}, "column_spacing": {"type": "fixed", "space": 6}, "cell_aspect_ratio": 1}""",
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
            config = """{"columns": {"type": "fixed", "count": 3}, "column_spacing": {"type": "fixed", "space": 6}, "row_spacing": {"type": "fixed", "space": 4}, "cell_aspect_ratio": 2}""",
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
            config = """{"columns": {"type": "fixed", "count": 3}}""",
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
            config = """{"columns": {"type": "fixed", "count": 2}}""",
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
            config = """{"columns": {"type": "fixed", "count": 2}}""",
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
            config = """{"columns": {"type": "fixed", "count": 2}}""",
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
    public fun testContentPositionPlacesTheRowsInATallerGrid() {
        val grid = grid(
            config = """{"columns": {"type": "fixed", "count": 2}, "content_position": {"horizontal": "start", "vertical": "center"}, "cell_aspect_ratio": 1}""",
            items = List(2) { box() }
        )
        layout(grid, width = 200, height = 300)

        assertEquals(100, cells(grid).first().top)
    }

    @Test
    public fun testAHiddenItemClosesUpWithoutRecreatingLaterCells() {
        val grid = grid(config = """{"columns": {"type": "fixed", "count": 3}, "cell_aspect_ratio": 1}""", items = List(4) { box() })
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
            config = """{"columns": {"type": "fit", "max": 4, "min_width": {"type": "fixed", "size": 50}}, "column_spacing": {"type": "fixed", "space": 10}}""",
            items = List(6) { box() }
        )
        grid.measure(unspecified(), unspecified())

        assertEquals(4 * 50 + 3 * 10, grid.measuredWidth)
    }

    @Test
    public fun testTheConfigIsPickedByTheGridsWidth() {
        val grid = grid(
            config = """{"columns": {"type": "fixed", "count": 2}, "cell_aspect_ratio": 1}""",
            overrides = """[{"when_state_matches": ${gridWidth("""{"at_least": 480}""")}, "value": {"columns": {"type": "fixed", "count": 6}, "cell_aspect_ratio": 1}}]""",
            items = List(6) { box() }
        )
        layout(grid, width = 479)
        assertEquals(2, cells(grid).count { it.top == 0 })

        layout(grid, width = 480)
        assertEquals(6, cells(grid).count { it.top == 0 })
    }

    @Test
    public fun testEachItemResolvesItsSpanFromItsOwnSelectors() {
        val half = """
            {"size": {"width": "100%", "height": 20},
             "config": {"column_span": 6},
             "overrides": {"config": [{"when_state_matches": ${gridWidth("""{"at_most": 280}""")}, "value": {"column_span": 12}}]},
             "view": {"type": "empty_view"}}
        """.trimIndent()
        val third = """
            {"size": {"width": "100%", "height": 20}, "config": {"column_span": 4},
             "view": {"type": "empty_view"}}
        """.trimIndent()
        val grid = grid(
            config = """{"columns": {"type": "fixed", "count": 12}}""",
            items = listOf(half, half, third, third, third)
        )

        layout(grid, width = 360)
        assertEquals("two halves, then three thirds", listOf(0, 0, 20, 20, 20), cells(grid).map { it.top })
        assertEquals(listOf(180, 180, 120, 120, 120), cells(grid).map { it.width })

        layout(grid, width = 250)
        assertEquals("each half on its own row", listOf(0, 20, 40, 40, 40), cells(grid).map { it.top })
        assertEquals(250, cells(grid)[0].width)
    }

    @Test
    public fun testCappedColumnsStopGrowingAndTheBlockIsCentred() {
        val grid = grid(
            config = capped(max = 11, min = 44, maxWidth = 64, partial = "center", horizontal = "center"),
            items = List(7) { box() }
        )
        layout(grid, width = 1000)

        // 11 fit, but their share passes the 64 cap: a block of 11 x 64 + 10 x 8, centred, and
        // the one short row of 7 centred within it.
        val block = 11 * 64 + 10 * 8
        assertEquals(64, cells(grid)[0].width)
        assertEquals((1000 - block) / 2 + (11 - 7) * (64 + 8) / 2, cells(grid)[0].left)
    }

    @Test
    public fun testAShortRowIsAlignedWithinTheColumnsNotTheGrid() {
        val grid = grid(
            config = capped(max = 4, min = 44, maxWidth = 64, partial = "end", horizontal = "start"),
            items = List(7) { box() }
        )
        layout(grid, width = 1000)

        val blockRight = 4 * 64 + 3 * 8
        assertEquals("rows of 4 and 3", listOf(0, 0, 0, 0, 72, 72, 72), cells(grid).map { it.top })
        assertEquals(blockRight, cells(grid)[3].right)
        assertEquals("the short row ends with the block", blockRight, cells(grid)[6].right)
    }

    @Test
    public fun testMatchingMinAndMaxKeepEveryColumnOneSize() {
        val grid = grid(
            config = capped(max = 11, min = 40, maxWidth = 40, partial = "center", horizontal = "end"),
            items = List(7) { box() }
        )
        layout(grid, width = 300)

        // floor((300 + 8) / 48) = 6 columns of exactly 40, the block at the end.
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 48), cells(grid).map { it.top })
        assertTrue(cells(grid).all { it.width == 40 })
        assertEquals(300, cells(grid)[5].right)
    }

    @Test
    public fun testTheBlocksStartIsTheRightEdgeInRtl() {
        ApplicationProvider.getApplicationContext<Context>().applicationInfo.flags =
            ApplicationProvider.getApplicationContext<Context>().applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val grid = grid(
            config = capped(max = 4, min = 44, maxWidth = 64, partial = "start", horizontal = "start"),
            items = List(4) { box() }
        )
        grid.layoutDirection = View.LAYOUT_DIRECTION_RTL
        layout(grid, width = 1000)

        assertEquals("the first column at the right edge", 1000, cells(grid)[0].right)
    }

    private fun capped(max: Int, min: Int, maxWidth: Int, partial: String, horizontal: String) = """
        {"columns": {"type": "fit", "max": $max,
                     "min_width": {"type": "fixed", "size": $min},
                     "max_width": {"type": "fixed", "size": $maxWidth}},
         "column_spacing": {"type": "fixed", "space": 8}, "row_spacing": {"type": "fixed", "space": 8}, "cell_aspect_ratio": 1,
         "partial_row_position": "$partial",
         "content_position": {"horizontal": "$horizontal", "vertical": "top"}}
    """.trimIndent()

    @Test
    public fun testAContainerFillsItsItemsAspectRatioBox() {
        val box = """
            {"size": {"width": "100%", "height": "auto", "aspect_ratio": 1},
             "view": {"type": "container",
                      "border": {"stroke_width": 5, "stroke_color": {"default": {"type": "hex", "hex": "#CCCCCC", "alpha": 1}}},
                      "items": [{"position": {"horizontal": "center", "vertical": "center"},
                                 "size": {"width": "auto", "height": "auto"},
                                 "view": ${label("c")}}]}}
        """.trimIndent()
        val grid = grid(config = """{"columns": {"type": "fixed", "count": 3}}""", items = List(3) { box })
        layout(grid, width = 300)

        val cell = cells(grid)[0]
        val container = items(grid)[0]
        assertEquals("square", cell.width, container.height)
        assertTrue("and inside its cell", container.bottom <= cell.height)
    }

    @Test
    public fun testAHeaderAndFooterLineUpWithACappedBlockAndStayOutOfTheList() {
        val grid = grid(
            config = capped(max = 4, min = 44, maxWidth = 64, partial = "start", horizontal = "start"),
            items = List(7) { box() },
            extra = """
                "accessibility_role": {"type": "list_view"},
                "header": {"size": {"width": "auto", "height": "auto"},
                           "position": {"horizontal": "start", "vertical": "center"}, "view": ${label("low")}},
                "footer": {"size": {"width": "auto", "height": "auto"},
                           "position": {"horizontal": "end", "vertical": "center"}, "view": ${label("high")}},
            """.trimIndent()
        )
        layout(grid, width = 400)

        val all = cells(grid)
        val (header, footer) = all.first() to all.last()
        val itemCells = all.drop(1).dropLast(1)
        val blockRight = 4 * 64 + 3 * 8
        assertEquals("the header spans the block", blockRight, header.width)
        assertEquals("the low label at the block's start", 0, header.left + (header as ViewGroup).getChildAt(0).left)
        assertEquals("the high label at the block's end", blockRight, footer.left + (footer as ViewGroup).getChildAt(0).right)
        assertTrue("the header above the cells", header.bottom + 8 <= itemCells.first().top)
        assertTrue("the footer below them", footer.top >= itemCells.last().bottom + 8)

        val info = android.view.accessibility.AccessibilityNodeInfo.obtain()
        grid.onInitializeAccessibilityNodeInfo(info)
        assertEquals("only the seven items are list items", 7, info.collectionInfo.rowCount)
    }

    @Test
    public fun testAHeaderAndFooterKeepTheirOwnHeightBesideRatioRows() {
        val grid = grid(
            config = capped(max = 4, min = 44, maxWidth = 64, partial = "start", horizontal = "start"),
            items = List(7) { box() },
            extra = """"header": {"view": ${label("heading")}}, "footer": {"view": ${label("note")}},"""
        )
        layout(grid, width = 400)

        val all = cells(grid)
        assertEquals(64, all[1].height)
        assertTrue("the header's own height: ${all.first().height}", all.first().height < 40)
        assertTrue("the footer's own height: ${all.last().height}", all.last().height < 40)
    }

    @Test
    public fun testAHiddenHeaderLeavesNoRow() {
        val grid = grid(
            config = capped(max = 4, min = 44, maxWidth = 64, partial = "start", horizontal = "start"),
            items = List(7) { box() },
            extra = """"header": {"view": ${label("hidden heading")}},"""
        )
        (cells(grid).first() as ViewGroup).getChildAt(0).visibility = View.GONE
        layout(grid, width = 400)

        assertEquals(0, cells(grid)[1].top)
    }

    @Test
    public fun testSpreadGapsLetCappedColumnsReachBothEdges() {
        val grid = grid(config = spreadColumns(maxSpace = null, horizontal = "start"), items = List(7) { box() })
        layout(grid, width = 340)

        // 4 x 64 of 340 leaves 84 for 3 gaps of 28.
        val cells = cells(grid)
        assertTrue(cells.all { it.width == 64 })
        assertEquals(listOf(0, 92, 184, 276), cells.take(4).map { it.left })
        assertEquals("one column short and centred: half a column plus half a gap in", 46, cells[4].left)
    }

    @Test
    public fun testSpreadGapsStopAtMaxSpaceAndTheRestIsPlaced() {
        val grid = grid(config = spreadColumns(maxSpace = 20, horizontal = "center"), items = List(4) { box() })
        layout(grid, width = 340)

        // Gaps would be 28; they stop at 20, and the 24 over centres the block.
        assertEquals(listOf(12, 96, 180, 264), cells(grid).map { it.left })
    }

    @Test
    public fun testSpreadGapsMirrorInRtl() {
        ApplicationProvider.getApplicationContext<Context>().applicationInfo.flags =
            ApplicationProvider.getApplicationContext<Context>().applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val grid = grid(config = spreadColumns(maxSpace = null, horizontal = "start"), items = List(7) { box() })
        grid.layoutDirection = View.LAYOUT_DIRECTION_RTL
        layout(grid, width = 340)

        val cells = cells(grid)
        assertEquals(340, cells[0].right)
        assertEquals(46, 340 - cells[4].right)
    }

    @Test
    public fun testSpreadRowsFillAHeightTheGridHasOfItsOwn() {
        val grid = grid(config = spreadRows(maxSpace = null), items = List(3) { box(size = """{"width": "100%", "height": 32}""") })
        layout(grid, width = 200, height = 300)

        // Three 32 rows in 300 leave 204 for 2 gaps of 102.
        assertEquals(listOf(0, 134, 268), cells(grid).map { it.top })
    }

    @Test
    public fun testSpreadRowsStopAtMaxSpaceAndTheRestIsPlaced() {
        val grid = grid(config = spreadRows(maxSpace = 40), items = List(3) { box(size = """{"width": "100%", "height": 32}""") })
        layout(grid, width = 200, height = 300)

        // Gaps stop at 40: 96 + 80 of 300 leaves 124, centred 62 down.
        assertEquals(listOf(62, 134, 206), cells(grid).map { it.top })
    }

    @Test
    public fun testAnAutoHeightGridsRowsSitAtTheMinimumSpace() {
        val grid = grid(config = spreadRows(maxSpace = null), items = List(3) { box(size = """{"width": "100%", "height": 32}""") })
        layout(grid, width = 200)

        assertEquals(listOf(0, 40, 80), cells(grid).map { it.top })
    }

    @Test
    public fun testShowsItsWidthToTheViewsInsideItsCells() {
        val hidden = """
            {"size": {"width": "100%", "height": 20},
             "view": {"type": "empty_view",
                      "visibility": {"default": true, "invert_when_state_matches": ${gridWidth("""{"at_least": 0}""")}}}}
        """.trimIndent()
        // Collectors run inline, as on Dispatchers.Main.immediate, so a publish mid-layout would lose its layout requests.
        Dispatchers.setMain(UnconfinedTestDispatcher(testDispatcher.scheduler))
        val environment = realEnvironment()
        val grid = grid(config = """{"columns": {"type": "fixed", "count": 2}}""", items = listOf(hidden, box()), environment = environment)

        // Laid out by the window's own traversal, where a publish from inside layout would lose its requests.
        nextFrame()
        nextFrame()

        assertFalse("hidden by the grid's width", items(grid)[0].isVisible)
        assertTrue(items(grid)[1].isVisible)
        assertEquals("and the grid is laid out again, closing up its column", 0, cells(grid)[1].left)
    }

    @Test
    public fun testMatchesItsWidthInsideTheBorderItDraws() {
        Dispatchers.setMain(UnconfinedTestDispatcher(testDispatcher.scheduler))
        val stroke = { width: Int -> """{"stroke_width": $width, "stroke_color": {"default": {"type": "hex", "hex": "#000000", "alpha": 1}}}""" }
        // 320 less the overriding stroke of 12 drawn on both sides is 296; less the declared 4 would be 312.
        val grid = build(
            """
            {"type": "grid_layout", "identifier": "grid",
             "config": {"columns": {"type": "fixed", "count": 2}},
             "border": ${stroke(4)},
             "view_overrides": {
               "config": [
                 {"when_state_matches": ${gridWidth("""{"at_least": 297}""")}, "value": {"columns": {"type": "fixed", "count": 4}}},
                 {"when_state_matches": ${gridWidth("""{"at_least": 296}""")}, "value": {"columns": {"type": "fixed", "count": 3}}}
               ],
               "border": [{"value": ${stroke(12)}}]
             },
             "items": [${List(4) { box(size = """{"width": "100%", "height": 20}""") }.joinToString()}]}
            """.trimIndent(),
            realEnvironment()
        ) as ViewGroup
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(FrameLayout(activity).apply { addView(grid) })
        nextFrame()
        nextFrame()

        assertEquals("the window it was laid out in", 320, grid.width)
        assertEquals("the overriding stroke is drawn", 12, grid.paddingLeft)
        assertEquals("three columns, matched at 296", 3, cells(grid).count { it.top == cells(grid)[0].top })
    }

    @Test
    public fun testItemsPlaceInTheOrderTheirConfigResolvesTo() {
        val first = """
            {"size": {"width": "100%", "height": 20},
             "config": {"order": 0},
             "view": {"type": "empty_view"}}
        """.trimIndent()
        val second = """
            {"size": {"width": "100%", "height": 20},
             "config": {"order": 0},
             "overrides": {"config": [{"when_state_matches": ${gridWidth("""{"at_least": 400}""")}, "value": {"order": -1}}]},
             "view": {"type": "empty_view"}}
        """.trimIndent()
        val grid = grid(config = """{"columns": {"type": "fixed", "count": 2}}""", items = listOf(first, second))

        layout(grid, width = 300)
        assertEquals("list order when narrow", listOf(0, 150), cells(grid).map { it.left })

        layout(grid, width = 500)
        assertEquals("the second first when wide", listOf(250, 0), cells(grid).map { it.left })
    }

    @Test
    public fun testAShuffleMixesOnlyItemsOfOneOrderAndTheHeaderStaysFirst() {
        val options = listOf("a", "b", "c", "d", "e").map {
            """{"size": {"width": "100%", "height": 20}, "view": ${label(it)}}"""
        }
        val none = """
            {"size": {"width": "100%", "height": 20}, "config": {"order": 1},
             "view": ${label("None")}}
        """.trimIndent()
        val grid = grid(
            config = """{"columns": {"type": "fixed", "count": 1}}""",
            items = options + none,
            extra = """"randomize_children": true, "header": {"view": ${label("top")}},""",
            environment = realEnvironment()
        )
        layout(grid, width = 200)

        // ui-test:grid:6 shuffles to c b e d None a; sorting by order then moves None to the end.
        val read = cells(grid).sortedBy { it.top }
            .map { ((it as ViewGroup).getChildAt(0) as android.widget.TextView).text.toString() }
        assertEquals(listOf("top", "c", "b", "e", "d", "a", "None"), read)
    }

    @Test
    public fun testPublishesTheWidthAParentMeasuresExactlyAfterAnUnboundedProbe() {
        val environment = realEnvironment()
        val json = """
            {"type": "grid_layout", "identifier": "grid",
             "config": {"columns": {"type": "fit", "max": 3, "min_width": {"type": "fixed", "size": 40}},
                        "column_spacing": {"type": "fixed", "space": 8}},
             "items": [${List(3) { box(size = """{"width": "100%", "height": 40}""") }.joinToString()}]}
        """.trimIndent()
        val model = LayoutViewModel().getOrCreateModel(
            ViewInfo.viewInfoFromJson(JsonValue.parseString(json).requireMap()), environment
        ) as GridLayoutModel
        val grid = model.createView(ApplicationProvider.getApplicationContext(), viewEnv, null)

        // Laid out once at another width, so no pending layout forces the measures below to run.
        layout(grid, width = 300)
        // A hugging parent probes unbounded, then measures exactly at the size that gave.
        grid.measure(unspecified(), unspecified())
        val (width, height) = grid.measuredWidth to grid.measuredHeight
        grid.measure(exactly(width), exactly(height))
        grid.layout(0, 0, width, height)
        nextFrame()

        assertEquals("3 x 40 + 2 x 8", 136f, model.gridState.changes.value.width)
    }

    @Test
    public fun testLaysOutAgainOnlyWhenAStateChangeChangesAConfig() {
        val wide = """{"scope": ["wide"], "value": {"equals": true}}"""
        val model = LayoutViewModel().getOrCreateModel(
            ViewInfo.viewInfoFromJson(
                JsonValue.parseString(
                    """
                    {"type": "grid_layout", "identifier": "grid",
                     "config": {"columns": {"type": "fixed", "count": 2}},
                     "view_overrides": {"config": [{"when_state_matches": $wide, "value": {"columns": {"type": "fixed", "count": 4}}}]},
                     "items": [${List(4) { box() }.joinToString()}]}
                    """.trimIndent()
                ).requireMap()
            ),
            mockEnv
        )
        val grid = model.createView(ApplicationProvider.getApplicationContext(), viewEnv, null)
        layout(grid, width = 400)

        model.listener?.onStateUpdated(stateWith("other" to true))
        assertFalse("a key no override reads", grid.isLayoutRequested)

        model.listener?.onStateUpdated(stateWith("wide" to true))
        assertTrue("the override now matches", grid.isLayoutRequested)
    }

    /** A state whose layout scope holds [entry]. */
    private fun stateWith(entry: Pair<String, Boolean>) = ThomasState(
        layout = State.Layout(identifier = "test-layout").apply { state = mapOf(entry.first to JsonValue.wrap(entry.second)) },
        form = null, pager = null, video = null, asyncView = null,
        capabilities = ThomasCapabilities.NONE
    )

    private fun spreadColumns(maxSpace: Int?, horizontal: String) = """
        {"columns": {"type": "fit", "max": 4,
                     "min_width": {"type": "fixed", "size": 44},
                     "max_width": {"type": "fixed", "size": 64}},
         "column_spacing": {"type": "spread", "min_space": 6${maxSpace?.let { """, "max_space": $it""" } ?: ""}},
         "cell_aspect_ratio": 1,
         "partial_row_position": "center",
         "content_position": {"horizontal": "$horizontal", "vertical": "top"}}
    """.trimIndent()

    private fun spreadRows(maxSpace: Int?) = """
        {"columns": {"type": "fixed", "count": 1},
         "row_spacing": {"type": "spread", "min_space": 8${maxSpace?.let { """, "max_space": $it""" } ?: ""}},
         "content_position": {"horizontal": "start", "vertical": "center"}}
    """.trimIndent()

    @Test
    public fun testAnUnboundedFitGridIsAWidthBasisForAPercentSibling() {
        val stack = stack(
            direction = "horizontal",
            items = listOf(
                """
                {"size": {"width": "auto", "height": "auto"},
                 "view": {"type": "grid_layout", "identifier": "grid",
                          "config": {"columns": {"type": "fit", "max": 3,
                                                 "min_width": {"type": "fixed", "size": 40}},
                                     "column_spacing": {"type": "fixed", "space": 8}},
                          "items": [${List(3) { box(size = """{"width": "100%", "height": 40}""") }.joinToString()}]}}
                """,
                """{"size": {"width": "50%", "height": 40}, "view": {"type": "empty_view"}}"""
            )
        )
        // A horizontal scroll's content is measured with no width, as in a version 1 scene.
        stack.measure(unspecified(), unspecified())
        stack.layout(0, 0, stack.measuredWidth, stack.measuredHeight)

        val grid = (stack as ViewGroup).getChildAt(0)
        assertEquals("3 x 40 + 2 x 8", 136, grid.width)
        assertEquals("the stack solves 136 / (1 - 0.5)", 272, stack.width)
        assertEquals(136, stack.getChildAt(1).width)
    }

    @Test
    public fun testASpanThatDoesNotFitStartsANewRow() {
        val grid = grid(
            config = """{"columns": {"type": "fixed", "count": 3}, "cell_aspect_ratio": 1}""",
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
                 "view": ${grid("""{"columns": {"type": "fixed", "count": 4}, "column_spacing": {"type": "fixed", "space": 8}, "cell_aspect_ratio": 1}""")}}
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
                 "view": ${grid("""{"columns": {"type": "fixed", "count": 4}, "column_spacing": {"type": "fixed", "space": 8}, "cell_aspect_ratio": 1}""")}}
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
                 "view": ${grid("""{"columns": {"type": "fixed", "count": 4}, "column_spacing": {"type": "fixed", "space": 8}, "cell_aspect_ratio": 1}""")}}
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
                 "view": {"type": "grid_layout", "identifier": "grid", "config": {"columns": {"type": "fixed", "count": 2}, "column_spacing": {"type": "fixed", "space": 8}},
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
                 "view": ${grid("""{"columns": {"type": "fixed", "count": 2}, "column_spacing": {"type": "fixed", "space": 8}, "cell_aspect_ratio": 1}""")}}
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
                 "view": ${grid("""{"columns": {"type": "fixed", "count": 2}, "column_spacing": {"type": "fixed", "space": 8}, "cell_aspect_ratio": 1}""")}}
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
    public fun testAPercentHeightAutoGridTakesItsShareOfTheRow() {
        val stack = stack(
            direction = "horizontal",
            items = listOf(
                """{"size": {"width": 60, "height": 120}, "view": ${label("tall")}}""",
                """
                {"size": {"width": "auto", "height": "100%"},
                 "view": ${grid("""{"columns": {"type": "fixed", "count": 2}, "column_spacing": {"type": "fixed", "space": 8}}""")}}
                """
            )
        )
        layout(stack, width = 300)

        assertEquals("the grid fills the row's 120", 120, (stack as ViewGroup).getChildAt(1).height)
        assertEquals("and the row is the label's height", 120, stack.height)
    }

    @Test
    public fun testARowWithAnAutoGridDoesNotOverflowWhenPercentHeightsSettle() {
        val stack = stack(
            direction = "horizontal",
            items = listOf(
                """{"size": {"width": 60, "height": 40}, "view": {"type": "empty_view"}}""",
                """{"size": {"width": "auto", "height": "100%"},
                    "view": ${label("A label whose whole line is far wider than the share it is given here")}}""",
                """
                {"size": {"width": "auto", "height": "auto"},
                 "view": ${grid("""{"columns": {"type": "fixed", "count": 2}, "column_spacing": {"type": "fixed", "space": 8}, "cell_aspect_ratio": 1}""")}}
                """
            )
        )
        layout(stack, width = 300)

        val last = (stack as ViewGroup).getChildAt(2)
        assertTrue("nothing runs past the row: ${last.right}", last.right <= 300)
    }

    @Test
    public fun testAnAutoGridInAListItemStillFillsItsRow() {
        val stack = build(
            """
            {"type": "linear_layout", "direction": "horizontal", "accessibility_role": {"type": "list_view"},
             "items": [
               {"size": {"width": "auto", "height": "auto"}, "accessibility_role": {"type": "list_item"},
                "view": ${grid("""{"columns": {"type": "fixed", "count": 4}, "column_spacing": {"type": "fixed", "space": 8}, "cell_aspect_ratio": 1}""")}},
               {"size": {"width": "auto", "height": "auto"}, "view": ${label("beside")}}
             ]}
            """.trimIndent()
        ).also { view ->
            Robolectric.buildActivity(Activity::class.java).setup().get()
                .setContentView(FrameLayout(view.context).apply { addView(view) })
        }
        layout(stack, width = 300)

        val wrapper = (stack as ViewGroup).getChildAt(0)
        val beside = stack.getChildAt(1)
        assertEquals("the grid's list item takes the rest", 300 - beside.width, wrapper.width)
    }

    @Test
    public fun testAFullHeightItemsContentGrowsARowWithABasis() {
        val longText = "Now much longer text that needs several more lines in this same cell"
        val grid = grid(
            config = """{"columns": {"type": "fixed", "count": 2}}""",
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
            config = """{"columns": {"type": "fixed", "count": 2}}""",
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
        {"type": "grid_layout", "identifier": "grid", "config": $config,
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

    private fun grid(
        config: String,
        items: List<String>,
        overrides: String? = null,
        extra: String = "",
        environment: ModelEnvironment = mockEnv
    ): ViewGroup =
        build(
            """
            {
              "type": "grid_layout",
              "identifier": "grid",
              $extra
              "config": $config,
              ${overrides?.let { """"view_overrides": {"config": $it},""" } ?: ""}
              "items": [${items.joinToString()}]
            }
            """.trimIndent(),
            environment
        ).also { view ->
            Robolectric.buildActivity(Activity::class.java).setup().get()
                .setContentView(FrameLayout(view.context).apply { addView(view) })
        } as ViewGroup

    private fun build(json: String, environment: ModelEnvironment = mockEnv): View {
        val info = ViewInfo.viewInfoFromJson(JsonValue.parseString(json).requireMap())
        return LayoutViewModel().getOrCreateModel(info, environment)
            .createView(ApplicationProvider.getApplicationContext(), viewEnv, null)
    }

    /** Runs the next frame's callbacks, where the grid publishes its width, and what they start. */
    private fun nextFrame() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
        testDispatcher.scheduler.advanceUntilIdle()
    }

    /** A real environment, so state reaches the grid's descendants, which [mockEnv]'s don't. */
    private fun realEnvironment() = ModelEnvironment(
        layoutState = LayoutState.EMPTY,
        reporter = mockk(relaxUnitFun = true),
        actionsRunner = mockk(relaxUnitFun = true),
        displayTimer = mockk(relaxed = true),
        modelScope = testScope,
        shuffleSeed = UI_TEST_RANDOM_SEED
    )

    /** A predicate on the grid's own width, as its overrides and its items' see it. */
    private fun gridWidth(range: String) = """{"scope": ["${'$'}grids", "current", "width"], "value": $range}"""

    private fun box(
        size: String = """{"width": "100%", "height": "100%"}""",
        span: Int = 1,
        margin: String? = null
    ) = """
        {
          "size": $size,
          "config": {"column_span": $span},
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

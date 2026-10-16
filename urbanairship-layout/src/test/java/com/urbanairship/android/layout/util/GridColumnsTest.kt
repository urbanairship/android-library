/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import com.urbanairship.android.layout.property.GridItemConfig
import com.urbanairship.android.layout.property.GridLayoutConfig
import com.urbanairship.android.layout.property.GridColumns
import com.urbanairship.android.layout.property.GridColumnWidth
import com.urbanairship.android.layout.property.GridSpacing
import com.urbanairship.android.layout.property.HorizontalPosition
import com.urbanairship.json.jsonMapOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

public class GridColumnsTest {

    private fun fixed(size: Float) = GridColumnWidth(GridColumnWidth.Type.FIXED, size)
    private fun textScaled(size: Float) = GridColumnWidth(GridColumnWidth.Type.TEXT_SCALED, size)

    /** Requests for items with these spans and no start. */
    private fun spans(vararg values: Int) = values.map { GridItemRequest(it) }

    /** Each item's row and whole start column. */
    private fun cells(placements: List<GridPlacement>) = placements.map { it.row to it.halfColumn / 2 }

    @Test
    public fun testFitsAsManyMinimumWidthColumnsAsTheWidthAllows() {
        val config = GridLayoutConfig(columns = GridColumns.Fit(max = 11, minWidth = fixed(44f)), columnSpacing = GridSpacing.Fixed(8f))
        assertEquals(6, gridColumnCount(config, 320f))
    }

    @Test
    public fun testCapsTheFitAtMax() {
        assertEquals(4, gridColumnCount(GridLayoutConfig(columns = GridColumns.Fit(max = 4, minWidth = fixed(44f))), 1000f))
    }

    @Test
    public fun testCountsAnExactFitAndAFitShortByLessThanTheEpsilon() {
        val config = GridLayoutConfig(columns = GridColumns.Fit(max = 10, minWidth = fixed(50f)), columnSpacing = GridSpacing.Fixed(10f))
        assertEquals(3, gridColumnCount(config, 170f))
        assertEquals(3, gridColumnCount(config, 169.6f))
        assertEquals(2, gridColumnCount(config, 169f))
    }

    @Test
    public fun testNeverDropsBelowOneColumn() {
        assertEquals(1, gridColumnCount(GridLayoutConfig(columns = GridColumns.Fit(max = 4, minWidth = fixed(200f))), 100f))
    }

    @Test
    public fun testDrawsFixedColumnsAtAnyWidthAndMaxFitColumnsWithoutAWidth() {
        assertEquals(3, gridColumnCount(GridLayoutConfig(columns = GridColumns.Fixed(count = 3)), 50f))
        assertEquals(3, gridColumnCount(GridLayoutConfig(columns = GridColumns.Fit(max = 3, minWidth = fixed(44f))), null))
    }

    @Test
    public fun testScalesTheMinimumByTheTextSizeOnlyWhenAskedTo() {
        val plain = GridLayoutConfig(columns = GridColumns.Fit(max = 11, minWidth = fixed(44f)), columnSpacing = GridSpacing.Fixed(8f))
        val scaled = plain.copy(columns = GridColumns.Fit(max = 11, minWidth = textScaled(44f)))
        assertEquals(6, gridColumnCount(plain, 320f, 2f))
        assertEquals(3, gridColumnCount(scaled, 320f, 2f))
        assertEquals(6, gridColumnCount(scaled, 320f, 1f))
    }

    @Test
    public fun testTreatsACountBelowOneAsOne() {
        assertEquals(1, gridColumnCount(GridLayoutConfig(columns = GridColumns.Fixed(count = 0)), 300f))
    }

    @Test
    public fun testFillsRowsInOrderAndLeavesOnlyTheLastOneShort() {
        val placed = placeGridItems(spans(1, 1, 1, 1, 1, 1, 1), 3, HorizontalPosition.START)
        assertEquals(listOf(0, 0, 0, 1, 1, 1, 2), placed.map { it.row })
        assertEquals(listOf(0, 2, 4, 0, 2, 4, 0), placed.map { it.halfColumn })
    }

    @Test
    public fun testOffsetsAShortRowByHalfColumnsForCenterAndEnd() {
        assertEquals(GridPlacement(1, 2, 1), placeGridItems(spans(1, 1, 1, 1), 3, HorizontalPosition.CENTER)[3])
        assertEquals(1, placeGridItems(spans(1, 1, 1, 1, 1), 3, HorizontalPosition.CENTER)[3].halfColumn)
        assertEquals(4, placeGridItems(spans(1, 1, 1, 1), 3, HorizontalPosition.END)[3].halfColumn)
    }

    @Test
    public fun testStartsANewRowForAnItemThatDoesNotFitWithoutBackfilling() {
        val placed = placeGridItems(spans(2, 2, 1), 3, HorizontalPosition.START)
        assertEquals(listOf(0, 1, 1), placed.map { it.row })
        assertEquals(listOf(0, 0, 4), placed.map { it.halfColumn })
    }

    @Test
    public fun testAlignsAnyShortRowNotJustTheLast() {
        val placed = placeGridItems(spans(2, 3), 3, HorizontalPosition.CENTER)
        assertEquals(GridPlacement(0, 1, 2), placed[0])
        assertEquals(GridPlacement(1, 0, 3), placed[1])
    }

    @Test
    public fun testClampsASpanToTheColumnCountAndToAtLeastOne() {
        val placed = placeGridItems(spans(5, 0), 3, HorizontalPosition.START)
        assertEquals(listOf(3, 1), placed.map { it.span })
        assertEquals(listOf(0, 1), placed.map { it.row })
    }

    @Test
    public fun testPlacesNothingForNoItems() {
        assertEquals(emptyList<GridPlacement>(), placeGridItems(emptyList(), 3, HorizontalPosition.START))
    }

    private fun fit(min: Float, max: Float? = null) = GridLayoutConfig(
        columns = GridColumns.Fit(max = 11, minWidth = fixed(min), maxWidth = max?.let(::fixed))
    )

    @Test
    public fun testMaxColumnWidthIsNullWithoutACapAndForFixedColumns() {
        assertNull(gridMaxColumnWidth(fit(44f), 1f))
        assertNull(gridMaxColumnWidth(GridLayoutConfig(columns = GridColumns.Fixed(count = 4)), 1f))
    }

    @Test
    public fun testMaxColumnWidthIsTheCapScaledWithTextWhenAsked() {
        assertEquals(64f, gridMaxColumnWidth(fit(44f, 64f), 1f))
        val scaled = GridLayoutConfig(
            columns = GridColumns.Fit(max = 11, minWidth = fixed(44f), maxWidth = textScaled(64f))
        )
        assertEquals(96f, gridMaxColumnWidth(scaled, 1.5f))
    }

    @Test
    public fun testMaxColumnWidthNeverFallsBelowTheMinimum() {
        assertEquals(44f, gridMaxColumnWidth(fit(44f, 30f), 1f))
    }

    @Test
    public fun testSpacingMinimumIsTheFixedSpaceOrTheSpreadMinimum() {
        assertEquals(8f, GridSpacing.Fixed(8f).min)
        assertEquals(6f, GridSpacing.Spread(minSpace = 6f).min)
        assertEquals(0f, GridSpacing.Fixed(-2f).min)
    }

    @Test
    public fun testColumnsAreCountedWithTheMinimumSpace() {
        // floor((340 + 6 + 0.5) / 50) = 6, capped at 4: spreading never adds or drops a column.
        val config = GridLayoutConfig(
            columns = GridColumns.Fit(max = 4, minWidth = fixed(44f)),
            columnSpacing = GridSpacing.Spread(minSpace = 6f)
        )
        assertEquals(4, gridColumnCount(config, 340f))
    }

    @Test
    public fun testSpreadSpaceSharesTheLeftoverWithinItsBounds() {
        assertEquals(28f, spreadSpace(GridSpacing.Spread(6f), leftover = 84f, count = 4))
        assertEquals("never under the minimum", 6f, spreadSpace(GridSpacing.Spread(6f), leftover = 14f, count = 4))
        assertEquals(20f, spreadSpace(GridSpacing.Spread(6f, maxSpace = 20f), leftover = 84f, count = 4))
        assertEquals(6f, spreadSpace(GridSpacing.Spread(6f, maxSpace = 20f), leftover = 6f, count = 4))
    }

    @Test
    public fun testSpreadSpaceTreatsAMaxBelowTheMinAsTheMin() {
        assertEquals(10f, spreadSpace(GridSpacing.Spread(10f, maxSpace = 4f), leftover = 84f, count = 4))
    }

    @Test
    public fun testSpreadSpaceDoesNothingForFixedSpacingOrFewerThanTwo() {
        assertNull(spreadSpace(GridSpacing.Fixed(6f), leftover = 84f, count = 4))
        assertNull(spreadSpace(GridSpacing.Spread(6f), leftover = 84f, count = 1))
    }

    @Test
    public fun testMinColumnWidthIsNullWithoutAPositiveMinimum() {
        assertNull(gridMinColumnWidth(GridLayoutConfig(columns = GridColumns.Fixed(count = 3)), 2f))
        assertEquals(3, gridColumnCount(GridLayoutConfig(columns = GridColumns.Fit(max = 3, minWidth = fixed(0f))), 50f))
        assertNull(gridMinColumnWidth(GridLayoutConfig(columns = GridColumns.Fit(max = 3, minWidth = fixed(0f))), 2f))
    }

    @Test
    public fun testScalesOnlyATextScaledMinimum() {
        assertEquals(44f, gridMinColumnWidth(GridLayoutConfig(columns = GridColumns.Fit(max = 3, minWidth = fixed(44f))), 1.5f))
        assertEquals(66f, gridMinColumnWidth(GridLayoutConfig(columns = GridColumns.Fit(max = 3, minWidth = textScaled(44f))), 1.5f))
    }

    @Test
    public fun testStartsAnItemAtItsColumn() {
        assertEquals(
            listOf(GridPlacement(0, 4, 8)),
            placeGridItems(listOf(GridItemRequest(8, start = 2)), 12, HorizontalPosition.START)
        )
    }

    @Test
    public fun testClampsAStartSoTheSpanFits() {
        val placed = placeGridItems(
            listOf(GridItemRequest(4, start = 10), GridItemRequest(4, start = -3), GridItemRequest(2, start = 5)),
            12,
            HorizontalPosition.START
        )
        // 10 clamps to 8; -3 to 0, a new row.
        assertEquals(listOf(0 to 8, 1 to 0, 1 to 5), cells(placed))
    }

    @Test
    public fun testKeepsACentredBlockValidAsTheColumnCountDrops() {
        // 8 wide from 2 in 12 columns; in 6 the span caps at 6 and the start at 0.
        assertEquals(
            listOf(GridPlacement(0, 0, 6)),
            placeGridItems(listOf(GridItemRequest(8, start = 2)), 6, HorizontalPosition.START)
        )
    }

    @Test
    public fun testBeginsANewRowForAStartBehindTheNextFreeColumn() {
        val placed = placeGridItems(listOf(GridItemRequest(6), GridItemRequest(4, start = 2)), 12, HorizontalPosition.START)
        assertEquals(listOf(0 to 0, 1 to 2), cells(placed))
    }

    @Test
    public fun testLeavesSkippedColumnsEmptyAndContinuesAfterTheStartedItem() {
        val placed = placeGridItems(
            listOf(GridItemRequest(2), GridItemRequest(2, start = 6), GridItemRequest(2), GridItemRequest(4)),
            12,
            HorizontalPosition.START
        )
        // Columns 2–5 stay empty; the third item follows at 8, the fourth doesn't fit.
        assertEquals(listOf(0 to 0, 0 to 6, 0 to 8, 1 to 0), cells(placed))
    }

    @Test
    public fun testDrawsARowWithAStartAsPlacedWhateverTheAlignment() {
        val placed = placeGridItems(
            listOf(GridItemRequest(4, start = 0), GridItemRequest(2), GridItemRequest(2)),
            12,
            HorizontalPosition.CENTER
        )
        assertEquals(listOf(0, 8, 12), placed.map { it.halfColumn })
    }

    @Test
    public fun testStillAlignsTheRowsWithoutAStart() {
        val placed = placeGridItems(
            listOf(
                GridItemRequest(12),
                GridItemRequest(4, start = 4),
                GridItemRequest(2),
                GridItemRequest(12),
                GridItemRequest(4)
            ),
            12,
            HorizontalPosition.CENTER
        )
        // Row 1 holds a start and stays put; row 3, short by 8, centres at half-column 8.
        assertEquals(listOf(0 to 0, 1 to 4, 1 to 8, 2 to 0, 3 to 4), cells(placed))
    }

    @Test
    public fun testReadsAFractionalColumnStartAsItsWholePart() {
        val config = GridItemConfig.fromJson(jsonMapOf("column_span" to 2, "column_start" to 5.7))
        assertEquals(5, config.columnStart)
    }
}

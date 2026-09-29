/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import com.urbanairship.android.layout.property.GridLayoutConfig
import com.urbanairship.android.layout.property.GridColumns
import com.urbanairship.android.layout.property.GridConfigSelector
import com.urbanairship.android.layout.property.GridConfigs
import com.urbanairship.android.layout.property.GridItemConfig
import com.urbanairship.android.layout.property.GridMinColumnWidth
import com.urbanairship.android.layout.property.GridSelectorConditions
import com.urbanairship.android.layout.property.HorizontalPosition
import com.urbanairship.android.layout.property.LengthRange
import com.urbanairship.android.layout.property.Orientation
import com.urbanairship.android.layout.property.Platform
import com.urbanairship.android.layout.property.WindowSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

public class GridColumnsTest {

    private fun fixed(points: Float) = GridMinColumnWidth(GridMinColumnWidth.Type.FIXED, points)
    private fun textScaled(points: Float) = GridMinColumnWidth(GridMinColumnWidth.Type.TEXT_SCALED, points)

    private val environment = GridEnvironment(
        platform = Platform.ANDROID,
        windowSize = WindowSize.SMALL,
        orientation = Orientation.PORTRAIT,
        gridWidth = 320f
    )

    @Test
    public fun testFitsAsManyMinimumWidthColumnsAsTheWidthAllows() {
        val config = GridLayoutConfig(columns = GridColumns.Fit(max = 11, minWidth = fixed(44f)), columnSpacing = 8f)
        assertEquals(6, gridColumnCount(config, 320f))
    }

    @Test
    public fun testCapsTheFitAtMax() {
        assertEquals(4, gridColumnCount(GridLayoutConfig(columns = GridColumns.Fit(max = 4, minWidth = fixed(44f))), 1000f))
    }

    @Test
    public fun testCountsAnExactFitAndAFitShortByLessThanTheEpsilon() {
        val config = GridLayoutConfig(columns = GridColumns.Fit(max = 10, minWidth = fixed(50f)), columnSpacing = 10f)
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
        val plain = GridLayoutConfig(columns = GridColumns.Fit(max = 11, minWidth = fixed(44f)), columnSpacing = 8f)
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
    public fun testRangeIncludesBothEnds() {
        val range = LengthRange(atLeast = 300f, atMost = 400f)
        assertTrue(300f in range)
        assertTrue(400f in range)
        assertFalse(400.1f in range)
        assertFalse(299.9f in LengthRange(atLeast = 300f))
        assertTrue(10_000f in LengthRange(atLeast = 300f))
        assertTrue(0f in LengthRange())
    }

    private val narrow = GridLayoutConfig(columns = GridColumns.Fixed(count = 2))
    private val wide = GridLayoutConfig(columns = GridColumns.Fixed(count = 6))
    private val tablet = GridLayoutConfig(columns = GridColumns.Fixed(count = 8))
    private val configs = GridConfigs(
        default = GridLayoutConfig(columns = GridColumns.Fixed(count = 4)),
        selectors = listOf(
            GridConfigSelector(GridSelectorConditions(gridWidth = LengthRange(atMost = 300f)), narrow),
            GridConfigSelector(GridSelectorConditions(windowSize = WindowSize.LARGE), tablet),
            GridConfigSelector(GridSelectorConditions(gridWidth = LengthRange(atLeast = 300f), platform = "android"), wide)
        )
    )

    @Test
    public fun testTakesTheFirstSelectorThatMatches() {
        assertSame(narrow, resolveGridConfig(configs, environment.copy(gridWidth = 200f)))
        assertSame(tablet, resolveGridConfig(configs, environment.copy(windowSize = WindowSize.LARGE)))
        assertSame(wide, resolveGridConfig(configs, environment))
    }

    @Test
    public fun testGivesASharedBoundaryToTheFirstSelectorListed() {
        assertSame(narrow, resolveGridConfig(configs, environment.copy(gridWidth = 300f)))
    }

    @Test
    public fun testRequiresEveryConditionASelectorNames() {
        assertSame(configs.default, resolveGridConfig(configs, environment.copy(platform = Platform.IOS)))
    }

    @Test
    public fun testAPlatformThisSdkDoesNotKnowNeverMatches() {
        val unknown = GridConfigs(
            default = GridLayoutConfig(columns = GridColumns.Fixed(count = 4)),
            selectors = listOf(GridConfigSelector(GridSelectorConditions(platform = "none"), wide))
        )
        assertSame(unknown.default, resolveGridConfig(unknown, environment))
    }

    @Test
    public fun testResolvesAnItemsConfigByTheSameRules() {
        val item = GridConfigs(
            default = GridItemConfig(columnSpan = 6),
            selectors = listOf(
                GridConfigSelector(GridSelectorConditions(gridWidth = LengthRange(atMost = 280f)), GridItemConfig(columnSpan = 12))
            )
        )
        assertEquals(12, resolveGridConfig(item, environment.copy(gridWidth = 250f)).columnSpan)
        assertEquals(6, resolveGridConfig(item, environment.copy(gridWidth = 300f)).columnSpan)
    }

    @Test
    public fun testSkipsGridWidthSelectorsForAGridWithNoWidth() {
        assertSame(configs.default, resolveGridConfig(configs, environment.copy(gridWidth = null)))
    }

    @Test
    public fun testFillsRowsInOrderAndLeavesOnlyTheLastOneShort() {
        val placed = placeGridItems(List(7) { 1 }, 3, HorizontalPosition.START)
        assertEquals(listOf(0, 0, 0, 1, 1, 1, 2), placed.map { it.row })
        assertEquals(listOf(0, 2, 4, 0, 2, 4, 0), placed.map { it.halfColumn })
    }

    @Test
    public fun testOffsetsAShortRowByHalfColumnsForCenterAndEnd() {
        assertEquals(GridPlacement(1, 2, 1), placeGridItems(List(4) { 1 }, 3, HorizontalPosition.CENTER)[3])
        assertEquals(1, placeGridItems(List(5) { 1 }, 3, HorizontalPosition.CENTER)[3].halfColumn)
        assertEquals(4, placeGridItems(List(4) { 1 }, 3, HorizontalPosition.END)[3].halfColumn)
    }

    @Test
    public fun testStartsANewRowForAnItemThatDoesNotFitWithoutBackfilling() {
        val placed = placeGridItems(listOf(2, 2, 1), 3, HorizontalPosition.START)
        assertEquals(listOf(0, 1, 1), placed.map { it.row })
        assertEquals(listOf(0, 0, 4), placed.map { it.halfColumn })
    }

    @Test
    public fun testAlignsAnyShortRowNotJustTheLast() {
        val placed = placeGridItems(listOf(2, 3), 3, HorizontalPosition.CENTER)
        assertEquals(GridPlacement(0, 1, 2), placed[0])
        assertEquals(GridPlacement(1, 0, 3), placed[1])
    }

    @Test
    public fun testClampsASpanToTheColumnCountAndToAtLeastOne() {
        val placed = placeGridItems(listOf(5, 0), 3, HorizontalPosition.START)
        assertEquals(listOf(3, 1), placed.map { it.span })
        assertEquals(listOf(0, 1), placed.map { it.row })
    }

    @Test
    public fun testPlacesNothingForNoItems() {
        assertEquals(emptyList<GridPlacement>(), placeGridItems(emptyList(), 3, HorizontalPosition.START))
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
}

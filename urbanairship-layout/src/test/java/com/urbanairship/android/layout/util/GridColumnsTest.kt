/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import com.urbanairship.android.layout.property.GridLayoutConfig
import com.urbanairship.android.layout.property.GridLayoutConfigSelector
import com.urbanairship.android.layout.property.GridLayoutConfigs
import com.urbanairship.android.layout.property.GridMinColumnWidth
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
        val config = GridLayoutConfig(maxColumns = 11, minColumnWidth = fixed(44f), columnSpacing = 8f)
        assertEquals(6, gridColumnCount(config, 320f))
    }

    @Test
    public fun testCapsTheFitAtMaxColumns() {
        assertEquals(4, gridColumnCount(GridLayoutConfig(maxColumns = 4, minColumnWidth = fixed(44f)), 1000f))
    }

    @Test
    public fun testCountsAnExactFitAndAFitShortByLessThanTheEpsilon() {
        val config = GridLayoutConfig(maxColumns = 10, minColumnWidth = fixed(50f), columnSpacing = 10f)
        assertEquals(3, gridColumnCount(config, 170f))
        assertEquals(3, gridColumnCount(config, 169.6f))
        assertEquals(2, gridColumnCount(config, 169f))
    }

    @Test
    public fun testNeverDropsBelowOneColumn() {
        assertEquals(1, gridColumnCount(GridLayoutConfig(maxColumns = 4, minColumnWidth = fixed(200f)), 100f))
    }

    @Test
    public fun testDrawsMaxColumnsWithoutAMinimumOrWithoutAWidth() {
        assertEquals(3, gridColumnCount(GridLayoutConfig(maxColumns = 3), 50f))
        assertEquals(3, gridColumnCount(GridLayoutConfig(maxColumns = 3, minColumnWidth = fixed(44f)), null))
    }

    @Test
    public fun testScalesTheMinimumByTheTextSizeOnlyWhenAskedTo() {
        val plain = GridLayoutConfig(maxColumns = 11, minColumnWidth = fixed(44f), columnSpacing = 8f)
        val scaled = plain.copy(minColumnWidth = textScaled(44f))
        assertEquals(6, gridColumnCount(plain, 320f, 2f))
        assertEquals(3, gridColumnCount(scaled, 320f, 2f))
        assertEquals(6, gridColumnCount(scaled, 320f, 1f))
    }

    @Test
    public fun testTreatsMaxColumnsBelowOneAsOne() {
        assertEquals(1, gridColumnCount(GridLayoutConfig(maxColumns = 0), 300f))
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

    private val narrow = GridLayoutConfig(maxColumns = 2)
    private val wide = GridLayoutConfig(maxColumns = 6)
    private val tablet = GridLayoutConfig(maxColumns = 8)
    private val configs = GridLayoutConfigs(
        default = GridLayoutConfig(maxColumns = 4),
        selectors = listOf(
            GridLayoutConfigSelector(gridWidth = LengthRange(atMost = 300f), config = narrow),
            GridLayoutConfigSelector(windowSize = WindowSize.LARGE, config = tablet),
            GridLayoutConfigSelector(gridWidth = LengthRange(atLeast = 300f), platform = "android", config = wide)
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
        val unknown = GridLayoutConfigs(
            default = GridLayoutConfig(maxColumns = 4),
            selectors = listOf(GridLayoutConfigSelector(platform = "none", config = wide))
        )
        assertSame(unknown.default, resolveGridConfig(unknown, environment))
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
        assertNull(gridMinColumnWidth(GridLayoutConfig(maxColumns = 3), 2f))
        assertNull(gridMinColumnWidth(GridLayoutConfig(maxColumns = 3, minColumnWidth = fixed(0f)), 2f))
    }

    @Test
    public fun testScalesOnlyATextScaledMinimum() {
        assertEquals(44f, gridMinColumnWidth(GridLayoutConfig(maxColumns = 3, minColumnWidth = fixed(44f)), 1.5f))
        assertEquals(66f, gridMinColumnWidth(GridLayoutConfig(maxColumns = 3, minColumnWidth = textScaled(44f)), 1.5f))
    }
}

package com.urbanairship.android.layout.util

import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the pure aspect-ratio fit math used by [ConstraintSetBuilder.aspectRatioWithinBounds]. */
class AspectRatioFitTest {

    @Test
    fun fitsReturnsNull() {
        // Portrait modal: width 80% of 1080 = 864, ratio 1.778 -> height 486, well within 1920.
        val fit = ConstraintSetBuilder.computeAspectRatioFit(
            requestedWidthPx = 864.0,
            requestedHeightPx = 864.0 / 1.778,
            ratio = 1.778,
            availableWidthPx = 1080,
            availableHeightPx = 1920
        )
        assertNull("A request that fits both bounds should take the unchanged ratio path", fit)
    }

    @Test
    fun overflowOnHeightAutoFitsWithinScreen() {
        // The original landscape repro: width 90% of 2160 = 1944, ratio 1.778 ->
        // derived height ~1093 > 1080 available. Expect a ~1920x1080 fitted box.
        val ratio = 1.778
        val knownWidth = 0.9 * 2160
        val fit = ConstraintSetBuilder.computeAspectRatioFit(
            requestedWidthPx = knownWidth,
            requestedHeightPx = knownWidth / ratio,
            ratio = ratio,
            availableWidthPx = 2160,
            availableHeightPx = 1080
        )
        assertNotNull(fit)
        fit!!
        // Height-bound: fitted height == available height.
        assertEquals(1080, fit.heightPx)
        assertEquals((1080 * ratio).toInt(), fit.widthPx)
        assertTrue("fitted width must not exceed available width", fit.widthPx <= 2160)
        assertTrue("fitted height must not exceed available height", fit.heightPx <= 1080)
    }

    @Test
    fun overflowOnWidthAutoFitsWithinScreen() {
        // height known = 1200, ratio 1.778 -> derived width 2133 > 2000 available width.
        val ratio = 1.778
        val knownHeight = 1200.0
        val fit = ConstraintSetBuilder.computeAspectRatioFit(
            requestedWidthPx = knownHeight * ratio,
            requestedHeightPx = knownHeight,
            ratio = ratio,
            availableWidthPx = 2000,
            availableHeightPx = 1200
        )
        assertNotNull(fit)
        fit!!
        // Width-bound: fitted width == available width.
        assertEquals(2000, fit.widthPx)
        assertEquals((2000 / ratio).roundToIntCompat(), fit.heightPx)
        assertTrue(fit.widthPx <= 2000)
        assertTrue(fit.heightPx <= 1200)
    }

    @Test
    fun twoAxisFitWithHorizontalMarginsClipsNeitherAxis() {
        // width 100% with horizontal margins: known width resolved against the full window
        // (2160) but available width is reduced by margins (1960). A tall ratio would make
        // availableHeight*ratio land between availW and the full window width -> must width-bound.
        val ratio = 2.0
        val knownWidth = 2160.0 // 100% of window
        val availW = 1960       // window - 200px margins
        val availH = 1080
        val fit = ConstraintSetBuilder.computeAspectRatioFit(
            requestedWidthPx = knownWidth,
            requestedHeightPx = knownWidth / ratio, // 1080, fits height exactly
            ratio = ratio,
            availableWidthPx = availW,
            availableHeightPx = availH
        )
        assertNotNull(fit)
        fit!!
        // availH * ratio = 2160 > availW(1960), so width is the binding axis.
        assertEquals(availW, fit.widthPx)
        assertTrue("must not clip horizontally", fit.widthPx <= availW)
        assertTrue("must not clip vertically", fit.heightPx <= availH)
    }

    @Test
    fun ratioPreservedInFittedPx() {
        val ratio = 1.778
        val fit = ConstraintSetBuilder.computeAspectRatioFit(
            requestedWidthPx = 5000.0,
            requestedHeightPx = 5000.0 / ratio,
            ratio = ratio,
            availableWidthPx = 2160,
            availableHeightPx = 1080
        )
        assertNotNull(fit)
        fit!!
        val actualRatio = fit.widthPx.toDouble() / fit.heightPx.toDouble()
        assertTrue(
            "ratio should be preserved (was $actualRatio, expected $ratio)",
            abs(actualRatio - ratio) < 0.01
        )
    }

    private fun Double.roundToIntCompat(): Int = this.roundToInt()
}

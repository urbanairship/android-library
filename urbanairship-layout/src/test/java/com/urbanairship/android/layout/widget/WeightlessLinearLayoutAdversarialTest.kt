/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Repros found reviewing the percent-in-auto-parent work (#2126, #2132).
 *
 * Each test asserts what the layout *should* produce. Sample scene:
 * `Scenes/Modal/_percent-in-auto-adversarial.yml`.
 */
@RunWith(RobolectricTestRunner::class)
public class WeightlessLinearLayoutAdversarialTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * An auto stack whose percentages sum to >= 1 takes the whole `AT_MOST` it was handed. When
     * that `AT_MOST` is a fixed-height ancestor's *remaining* budget rather than an exclusive
     * ceiling, everything measured after it is offered nothing and collapses.
     *
     * [hasAutoSizedAncestor] only catches this when the nearest ancestor owning a length is itself
     * auto. Here it is `EXACTLY` sized, so the stack claims 180 of the 300 and the footer is zeroed.
     * Renders correctly on the commit before #2126: nested 40, footer 30.
     */
    @Test
    public fun testNestedFullPercentStackDoesNotEatAFixedParentsRemainingSpace() {
        val outer = verticalStack()
        val header = FixedSizeView(context, 100, 120)
        val nested = verticalStack()
        val row = FixedSizeView(context, 100, 40)
        nested.addView(row, percentHeight(1f))
        val footer = FixedSizeView(context, 100, 30)

        outer.addView(header, fixedHeight(120))
        outer.addView(nested, autoHeight())
        outer.addView(footer, autoHeight())

        outer.measure(exactly(500), exactly(300))

        assertEquals("nested stack should size to its content", 40, nested.measuredHeight)
        assertTrue(
            "footer was starved (height ${footer.measuredHeight}, nested ${nested.measuredHeight})",
            footer.measuredHeight > 0
        )
    }

    /**
     * The same, one level deeper. An `auto` item defers to its content, so the question of whether
     * anything supplies a length has to pass down through it — a stack of percentages is no more
     * able to supply one than a percentage is, however many wrappers sit in between.
     */
    @Test
    public fun testLengthBasisWalkPassesThroughAutoSizedWrappers() {
        val outer = verticalStack()
        val header = FixedSizeView(context, 100, 120)
        val wrapper = verticalStack()
        val nested = verticalStack()
        val row = FixedSizeView(context, 100, 40)
        nested.addView(row, percentHeight(1f))
        wrapper.addView(nested, autoHeight())
        val footer = FixedSizeView(context, 100, 30)

        outer.addView(header, fixedHeight(120))
        outer.addView(wrapper, autoHeight())
        outer.addView(footer, autoHeight())

        outer.measure(exactly(500), exactly(300))

        assertEquals("wrapper should size to its content", 40, wrapper.measuredHeight)
        assertTrue(
            "footer was starved (height ${footer.measuredHeight}, wrapper ${wrapper.measuredHeight})",
            footer.measuredHeight > 0
        )
    }

    /**
     * The deferred `aspect_ratio` pass overwrites the reported height with
     * `totalLengthAfterFirstPass + ratio heights`. That first-pass total holds a percent child's
     * *margins* only, never the slot the distribution pass gave it, so an `AT_MOST` stack holding
     * both reports itself short by the whole percent slot and its children draw outside it.
     */
    @Test
    public fun testRatioChildBesidePercentChildStillReportsTheFullHeight() {
        val stack = verticalStack()
        val fixed = FixedSizeView(context, 100, 100)
        val percent = FixedSizeView(context, 100, 10)
        val ratio = FixedSizeView(context, 300, 10)

        stack.addView(fixed, fixedHeight(100))
        stack.addView(percent, percentHeight(0.5f))
        stack.addView(ratio, ratioAutoHeight(2f))

        stack.measure(exactly(300), atMost(1000))

        val children = fixed.measuredHeight + percent.measuredHeight + ratio.measuredHeight
        assertEquals(
            "stack under-reports its height, so the ratio row draws outside it",
            children, stack.measuredHeight
        )
    }

    /**
     * A cross-axis percent child is excluded from the stack's width in the first pass, but the
     * post-distribution rebuild — which only runs when some *other* child has a main-axis percent —
     * adds it back at its `WRAP_CONTENT` width. An unrelated percent-height row therefore changes
     * both the stack's width and the percent-width child's width.
     */
    @Test
    public fun testCrossAxisPercentWidthIsUnaffectedByAPercentHeightSibling() {
        val a = verticalStack()
        val barA = FixedSizeView(context, 800, 20)
        a.addView(barA, percentWidth(0.5f))
        a.addView(FixedSizeView(context, 100, 20), autoBoth())
        a.measure(atMost(1000), atMost(1000))

        val b = verticalStack()
        val barB = FixedSizeView(context, 800, 20)
        b.addView(barB, percentWidth(0.5f))
        b.addView(FixedSizeView(context, 100, 20), autoBoth())
        b.addView(FixedSizeView(context, 100, 20), percentHeight(0.5f))
        b.measure(atMost(1000), atMost(1000))

        assertEquals(
            "stack width changed because of an unrelated percent-height sibling",
            a.measuredWidth, b.measuredWidth
        )
        assertEquals(
            "percent-width child changed because of an unrelated percent-height sibling",
            barA.measuredWidth, barB.measuredWidth
        )
    }

    /**
     * The deferred cross-axis pass re-measures the child at its resolved width but pins the height
     * to what it measured at `WRAP_CONTENT`. Anything whose height depends on its width — a label,
     * most obviously — keeps its wide-measurement height and is clipped once it is narrowed.
     */
    @Test
    public fun testCrossAxisPercentChildKeepsAHeightThatMatchesItsResolvedWidth() {
        val stack = verticalStack()
        val label = ReflowView(context, area = 8000, maxWidth = 800)
        stack.addView(label, percentWidth(0.5f))
        stack.addView(FixedSizeView(context, 400, 20), autoBoth())
        stack.measure(atMost(1000), atMost(2000))

        val needed = 8000 / label.measuredWidth.coerceAtLeast(1)
        assertEquals(
            "height was pinned to the wide measurement, so content is clipped",
            needed, label.measuredHeight
        )
    }

    /**
     * Two rules for one thing. A child that is a percent on the cross axis *only* goes through the
     * deferred pass and resolves against the stack's measured width. A child that is a percent on
     * *both* axes never reaches that list — the main-axis distribution sizes it, and that path
     * still resolves the cross axis against the raw `AT_MOST` spec size.
     */
    @Test
    public fun testPercentOnBothAxesResolvesWidthTheSameWayAsPercentWidthAlone() {
        val stack = verticalStack()
        val widthOnly = FixedSizeView(context, 50, 26)
        val bothAxes = FixedSizeView(context, 50, 26)

        stack.addView(widthOnly, percentWidth(0.5f))
        stack.addView(bothAxes, WeightlessLinearLayout.LayoutParams(0, 0, 0.5f, 0.25f))
        stack.addView(FixedSizeView(context, 160, 26), autoBoth())
        stack.measure(atMost(1000), atMost(1000))

        assertEquals(
            "percent-on-both-axes used the spec ceiling as its width base",
            widthOnly.measuredWidth, bothAxes.measuredWidth
        )
    }

    /**
     * A percent item is a fraction of the whole stack, so `H = S / (1 - P)` needs `S` to be every
     * non-percent item — the ratio ones included. They're measured last, but their main-axis
     * extent comes from the cross axis and is settled before the solve runs, so leaving them out
     * made the percentage describe a length the stack never has.
     */
    @Test
    public fun testRatioSiblingCountsTowardThePercentSolve() {
        val stack = verticalStack()
        val fixed = FlexibleView(context)
        val percent = FlexibleView(context)
        val ratio = FlexibleView(context)

        stack.addView(fixed, fixedHeight(120))
        stack.addView(percent, percentHeight(0.5f))
        stack.addView(ratio, ratioAutoHeight(3f))

        stack.measure(exactly(325), atMost(1000))

        // 325 wide at 3:1 is a 108 tall row, so S = 120 + 108 and the stack solves to 456.
        assertEquals("ratio row", 108, ratio.measuredHeight)
        assertEquals(
            "the percent row should be half the finished stack",
            fixed.measuredHeight + ratio.measuredHeight, percent.measuredHeight
        )
        assertEquals("stack", 2 * percent.measuredHeight, stack.measuredHeight)
    }

    /** The width-axis twin: a ratio column counts toward `S` on a horizontal stack too. */
    @Test
    public fun testRatioSiblingCountsTowardThePercentSolveOnWidthAxis() {
        val stack = WeightlessLinearLayout(context)
        val fixed = FlexibleView(context)
        val percent = FlexibleView(context)
        val ratio = FlexibleView(context)

        stack.addView(fixed, WeightlessLinearLayout.LayoutParams(120, MATCH))
        stack.addView(percent, percentWidth(0.5f))
        stack.addView(ratio, WeightlessLinearLayout.LayoutParams(WRAP, WRAP, 0f, 0f, 0.5f))

        stack.measure(atMost(1000), exactly(200))

        // 200 tall at 1:2 is a 100 wide column, so S = 120 + 100 and the stack solves to 440.
        assertEquals("ratio column", 100, ratio.measuredWidth)
        assertEquals(
            "the percent column should be half the finished stack",
            fixed.measuredWidth + ratio.measuredWidth, percent.measuredWidth
        )
    }

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }

    private fun verticalStack() = WeightlessLinearLayout(context).apply {
        setOrientation(WeightlessLinearLayout.OrientationMode.VERTICAL)
    }

    private fun exactly(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
    private fun atMost(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.AT_MOST)

    private fun percentHeight(percent: Float) =
        WeightlessLinearLayout.LayoutParams(MATCH, 0, 0f, percent)

    private fun percentWidth(percent: Float) =
        WeightlessLinearLayout.LayoutParams(0, WRAP, percent, 0f)

    private fun fixedHeight(height: Int) = WeightlessLinearLayout.LayoutParams(MATCH, height)
    private fun autoHeight() = WeightlessLinearLayout.LayoutParams(MATCH, WRAP)
    private fun autoBoth() = WeightlessLinearLayout.LayoutParams(WRAP, WRAP)
    private fun ratioAutoHeight(ratio: Float) =
        WeightlessLinearLayout.LayoutParams(WRAP, WRAP, 0f, 0f, ratio)

    /** A view with a natural size that still honors EXACTLY/AT_MOST specs. */
    private class FixedSizeView(context: Context, val w: Int, val h: Int) : View(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
        }
    }

    /** Stands in for `empty_view`: no size of its own, takes whatever it is given. */
    private class FlexibleView(context: Context) : View(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(resolveSize(0, widthMeasureSpec), resolveSize(0, heightMeasureSpec))
        }
    }

    /** Stands in for a label: a fixed content area, with height derived from the width it gets. */
    private class ReflowView(context: Context, val area: Int, val maxWidth: Int) : View(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = resolveSize(maxWidth, widthMeasureSpec).coerceAtLeast(1)
            setMeasuredDimension(w, resolveSize((area + w - 1) / w, heightMeasureSpec))
        }
    }
}

/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.DynamicLayout
import android.text.PrecomputedText
import android.text.Spannable
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextUtils
import android.text.style.ClickableSpan
import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.LabelInfo
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.model.ItemProperties
import com.urbanairship.android.layout.model.LabelModel
import com.urbanairship.android.layout.model.ModelProperties
import com.urbanairship.android.layout.property.Size
import com.urbanairship.android.layout.util.ThomasViewIdResolver
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A label given a declared height draws only the lines that fit, and marks the rest with an
 * ellipsis. `maxLines` and `ellipsize` only work as a pair on a `StaticLayout`, so these cover the
 * mechanism that secures one — a `PrecomputedText` — the API level and layout properties that decide
 * who gets it, and the ellipsis it produces.
 *
 * These need [GraphicsMode.Mode.NATIVE]. `PrecomputedText` is only usable if the paint it was built
 * with still matches the view's, and that comparison is native: under Robolectric's legacy graphics
 * it is stubbed to fail, so `setText` rejects every `PrecomputedText` regardless of the paint.
 * Real measurement is what makes the ellipsis assertions mean anything, too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LabelTruncationTest {

    private val testScope = TestScope(StandardTestDispatcher())
    private val viewIdResolver = ThomasViewIdResolver()

    private val mockEnv: ModelEnvironment = mockk(relaxed = true) {
        every { modelScope } returns testScope
        every { viewIdResolver } returns this@LabelTruncationTest.viewIdResolver
        every { layoutState } returns mockk(relaxed = true)
    }

    private val viewEnvironment: ViewEnvironment = mockk(relaxed = true)

    @Test
    @Config(sdk = [29])
    public fun declaredHeight_onQ_precomputesAndEllipsizes() {
        val view = labelView(height = DECLARED_HEIGHT)

        assertTrue(view.text is PrecomputedText)
        assertEquals(TextUtils.TruncateAt.END, view.ellipsize)
    }

    @Test
    @Config(sdk = [34])
    public fun declaredHeight_onLaterApi_precomputesAndEllipsizes() {
        val view = labelView(height = DECLARED_HEIGHT)

        assertTrue(view.text is PrecomputedText)
        assertEquals(TextUtils.TruncateAt.END, view.ellipsize)
    }

    /**
     * AndroidX declines to build a framework `PrecomputedText` on 28 and we follow it, so a label
     * there is left exactly as it was before this behavior existed: no ellipsis, and the clamp in
     * `LabelView.onMeasure` stopping it at a whole line.
     */
    @Test
    @Config(sdk = [28])
    public fun declaredHeight_onPie_isLeftAlone() {
        val view = labelView(height = DECLARED_HEIGHT)

        assertFalse(view.text is PrecomputedText)
        assertNull(view.ellipsize)
    }

    @Test
    @Config(sdk = [26])
    public fun declaredHeight_onMinSdk_isLeftAlone() {
        val view = labelView(height = DECLARED_HEIGHT)

        assertFalse(view.text is PrecomputedText)
        assertNull(view.ellipsize)
    }

    /** An auto height grows to fit every line, so there is nothing to drop and nothing to mark. */
    @Test
    @Config(sdk = [29])
    public fun autoHeight_isLeftAlone() {
        val view = labelView(height = "auto")

        assertFalse(view.text is PrecomputedText)
        assertNull(view.ellipsize)
    }

    @Test
    @Config(sdk = [29])
    public fun declaredHeight_onQ_laysOutStatic() {
        val view = labelView(height = DECLARED_HEIGHT)
        measureAndLayout(view)

        assertTrue(view.layout is StaticLayout)
    }

    /** The bug this fixes: without a `PrecomputedText`, markdown text lands on a layout with no maximum. */
    @Test
    @Config(sdk = [28])
    public fun declaredHeight_onPie_laysOutDynamic() {
        val view = labelView(height = DECLARED_HEIGHT)
        measureAndLayout(view)

        assertTrue(view.layout is DynamicLayout)
    }

    /** The point of all of it: the last line the box has room for ends in an ellipsis. */
    @Test
    @Config(sdk = [29])
    public fun declaredHeight_onQ_drawsAnEllipsis() {
        val view = labelView(height = DECLARED_HEIGHT)
        measureAndLayout(view)

        val layout = view.layout
        assertEquals("should stop at the line count the box has room for", view.maxLines, layout.lineCount)
        assertTrue(
            "expected the last drawn line to end in an ellipsis",
            layout.getEllipsisCount(layout.lineCount - 1) > 0
        )
    }

    /**
     * The invariant the S21 broke: whatever the layout keeps has to end inside the box.
     *
     * On the device it broke because Arabic draws taller lines than the styled font's metrics
     * predict, so a count divided out of `getLineHeight` admitted one line more than fit — and
     * nothing was marked, because `StaticLayout` had been asked for exactly that many and judged
     * nothing dropped. Robolectric's font stack won't reproduce a taller fallback, so
     * `includeFontPadding` stands in for it: it makes the laid-out lines use the font's top and
     * bottom where `getLineHeight` reports ascent, descent and leading, opening the same kind of gap
     * between the estimate and the truth. `LayoutUtils.applyTextAppearance` turns it off for real
     * labels, which is why single-font text hid this until an unfamiliar script arrived.
     */
    @Test
    @Config(sdk = [29])
    public fun declaredHeight_onQ_keepsNoLinePastTheBox() {
        val view = labelView(height = DECLARED_HEIGHT)
        view.includeFontPadding = true
        measureAndLayout(view)

        assertLastLineInsideBox(view)
    }

    /** Same invariant where the arithmetic is tightest: a border eating into the box. */
    @Test
    @Config(sdk = [29])
    public fun declaredHeight_onQ_withStroke_keepsNoLinePastTheBox() {
        val view = labelView(height = DECLARED_HEIGHT)
        view.setPadding(STROKE, STROKE, STROKE, STROKE)
        measureAndLayout(view, height = 54)

        assertLastLineInsideBox(view)
    }

    /** Dropping the lines happens at every API level. Only the mark waits for 29. */
    @Test
    @Config(sdk = [28])
    public fun declaredHeight_onPie_stillClampsToWholeLines() {
        val view = labelView(height = DECLARED_HEIGHT)
        measureAndLayout(view)

        val layout = view.layout
        assertTrue("should have clamped", view.maxLines < layout.lineCount)
        assertTrue(
            "the lines it kept must fit the box",
            layout.getLineBottom(view.maxLines - 1) <= availableHeightOf(view)
        )
        assertEquals("nothing is marked below 29", 0, layout.getEllipsisCount(layout.lineCount - 1))
    }

    /**
     * Nothing may be drawn past the last line the clamp kept, with an 8pt border eating into the box
     * so the content height is not a whole multiple of the line height.
     *
     * Worth knowing what this does and does not prove. `LabelView.onDraw` clips to the line boundary,
     * and this asserts nothing escapes it — but the geometry here leaves an even amount of slack
     * above and below the kept lines, and with slack even the framework's own clip already lands on
     * the boundary, so this passes with or without that override. It guards the invariant; it does
     * not reproduce the partial lines seen on a device, which the override was written for.
     *
     * The label has no background here, so any non-transparent pixel below the cut is text.
     */
    @Test
    @Config(sdk = [28])
    public fun declaredHeight_onPie_withStroke_drawsNoPartialLine() {
        val view = labelView(height = DECLARED_HEIGHT)
        view.setPadding(STROKE, STROKE, STROKE, STROKE)
        measureAndLayout(view, height = 54)

        val layout = view.layout
        assertTrue("should have clamped", view.maxLines < layout.lineCount)

        val cut = view.extendedPaddingTop + layout.getLineTop(view.maxLines)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))

        for (y in cut until view.height) {
            for (x in 0 until view.width) {
                assertEquals(
                    "ink at ($x, $y), past the cut at $cut in a ${view.height}px view",
                    0,
                    bitmap.getPixel(x, y)
                )
            }
        }
    }

    /**
     * The reason a `PrecomputedText` was chosen over demoting the text to an immutable
     * `SpannedString`: it is itself a `Spannable`, so links stay dispatchable. A payload we never
     * see can rely on anything a `Spannable` offers, and this keeps all of it.
     */
    @Test
    @Config(sdk = [29])
    public fun precomputedText_keepsSpansAndMovementMethod() {
        val view = labelView(text = "Visit example.com for details", height = DECLARED_HEIGHT)
        val text = view.text

        assertTrue(text is PrecomputedText)
        assertTrue(text is Spannable)
        assertNotNull(view.movementMethod)

        val links = (text as Spanned).getSpans(0, text.length, ClickableSpan::class.java)
        assertTrue("expected a linkified span to survive precomputation", links.isNotEmpty())
    }

    /**
     * A drag must not pull the dropped text back into view. Below 29 the `DynamicLayout` stands
     * taller than the box, and the `LinkMovementMethodCompat` every label carries descends from
     * `ScrollingMovementMethod`, so without the pin a finger could read what was meant to be gone.
     */
    @Test
    @Config(sdk = [28])
    public fun declaredHeight_refusesToScrollVertically() {
        val view = labelView(height = DECLARED_HEIGHT)
        measureAndLayout(view)

        view.scrollTo(0, 40)
        assertEquals("scrollTo should be pinned", 0, view.scrollY)

        view.scrollBy(0, 40)
        assertEquals("scrollBy should be pinned", 0, view.scrollY)
    }

    /** An auto height grows to fit, so it has nothing to scroll and is left as it was. */
    @Test
    @Config(sdk = [28])
    public fun autoHeight_scrollingIsUntouched() {
        val view = labelView(height = "auto")
        measureAndLayout(view)

        view.scrollTo(0, 40)
        assertEquals(40, view.scrollY)
    }

    private fun availableHeightOf(view: LabelView): Int =
        view.measuredHeight - view.compoundPaddingTop - view.compoundPaddingBottom

    private fun assertLastLineInsideBox(view: LabelView) {
        val layout = view.layout
        val available = availableHeightOf(view)
        val lastBottom = layout.getLineBottom(layout.lineCount - 1)

        assertTrue(
            "kept ${layout.lineCount} lines ending at $lastBottom, box holds $available",
            lastBottom <= available
        )
    }

    /** Narrow and short enough that [LONG_TEXT] needs more lines than the box can hold. */
    private fun measureAndLayout(view: View, width: Int = 200, height: Int = 48) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun labelView(text: String = LONG_TEXT, height: String): LabelView {
        val info = ViewInfo.viewInfoFromJson(
            JsonValue.parseString(
                """
                {
                    "type": "label",
                    "text": "$text",
                    "text_appearance": {
                        "color": { "default": { "hex": "#000000", "alpha": 1 } }
                    }
                }
                """.trimIndent()
            ).requireMap()
        ) as LabelInfo

        val model = LabelModel(info, mockEnv, ModelProperties(pagerPageId = null))

        return model.createView(
            RuntimeEnvironment.getApplication(),
            viewEnvironment,
            ItemProperties(Size("100%", height))
        ) as LabelView
    }

    private companion object {
        /** Absolute, so `Size.height.isAuto` is false and the label truncates. */
        const val DECLARED_HEIGHT = "60"

        /** Stands in for a border stroke, which `LayoutUtils.addPadding` charges to the text. */
        const val STROKE = 8

        const val LONG_TEXT =
            "This label has a great deal more text in it than its declared height has any room " +
                "to draw, so the lines past the last one that fits are dropped rather than " +
                "sliced through the middle by whichever ancestor happens to clip, and the last " +
                "line that does fit is marked to say so."
    }
}

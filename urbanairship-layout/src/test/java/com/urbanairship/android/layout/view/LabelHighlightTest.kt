/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.Layout
import android.text.Spanned
import android.text.style.LineBackgroundSpan
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A `==highlight==` must not change how the text around it breaks.
 *
 * These need [GraphicsMode.Mode.NATIVE]: without real text measurement every line comes out the
 * same width and the overflow these check for cannot happen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LabelHighlightTest {

    private val testScope = TestScope(StandardTestDispatcher())
    private val viewIdResolver = ThomasViewIdResolver()

    private val mockEnv: ModelEnvironment = mockk(relaxed = true) {
        every { modelScope } returns testScope
        every { viewIdResolver } returns this@LabelHighlightTest.viewIdResolver
        every { layoutState } returns mockk(relaxed = true)
    }

    private val viewEnvironment: ViewEnvironment = mockk(relaxed = true)

    /**
     * The reported bug. A highlight wider than the room left on its line has to wrap like any other
     * text; if it is measured as one indivisible unit instead, it overruns the line and everything
     * past the edge is drawn where nobody can see it.
     */
    @Test
    @Config(sdk = [29])
    public fun highlightLongerThanALine_wraps() {
        val view = labelView(LONG_HIGHLIGHT)
        measureAndLayout(view)

        assertNoLineOverruns(view)
    }

    /** A highlight must not change where the text breaks compared with the same text unmarked. */
    @Test
    @Config(sdk = [29])
    public fun highlight_doesNotChangeLineBreaking() {
        val highlighted = labelView(LONG_HIGHLIGHT).also { measureAndLayout(it) }
        val plain = labelView(LONG_HIGHLIGHT.replace("==", "")).also { measureAndLayout(it) }

        assertEquals(
            "a highlight should not change the line count",
            plain.layout.lineCount,
            highlighted.layout.lineCount
        )
    }

    /** A short highlight, well inside one line, was never broken — kept so the fix doesn't regress it. */
    @Test
    @Config(sdk = [29])
    public fun shortHighlight_wraps() {
        val view = labelView("A ==brief== highlight sits inside its line and the rest of this text follows on.")
        measureAndLayout(view)

        assertNoLineOverruns(view)
    }

    /**
     * The background has to sit under the run itself. An offset on a line boundary belongs to two
     * lines and `getPrimaryHorizontal` answers for the later one, so asking it where this line ends
     * returns the far side of the line below — which drew the first line's highlight mirrored, over
     * the text *before* the run instead of the run.
     */
    @Test
    @Config(sdk = [29])
    public fun wrappedHighlight_coversTheRunAndNotWhatPrecedesIt() {
        val view = labelView(
            "A highlight should keep its ==yellow background and its rounded corners, and this " +
                "run is long enough to wrap== and a tail after it.",
            highlightHex = HIGHLIGHT_HEX,
            cornerRadius = 0
        )
        measureAndLayout(view)

        val layout = view.layout
        val spanned = view.text as Spanned
        val span = spanned.getSpans(0, spanned.length, LineBackgroundSpan::class.java).first()
        val spanStart = spanned.getSpanStart(span)

        val line = layout.getLineForOffset(spanStart)
        val runStartX = layout.getPrimaryHorizontal(spanStart)
        assertTrue("the run should not start at the line's left edge", runStartX > 8f)
        assertTrue("the run should wrap", layout.getLineForOffset(spanned.getSpanEnd(span)) > line)

        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))

        // Just below the line's top edge: inside the background rect, clear of the glyphs.
        val y = view.extendedPaddingTop + layout.getLineTop(line) + 2

        assertNotEquals(
            "nothing before the run starts should be highlighted",
            HIGHLIGHT_COLOR,
            bitmap.getPixel(2, y)
        )
        assertEquals(
            "the run itself should be highlighted",
            HIGHLIGHT_COLOR,
            bitmap.getPixel((runStartX + 6).toInt(), y)
        )
    }

    /**
     * The same thing in a right-to-left paragraph, where the two line edges swap places.
     *
     * A run reaching the *start* of its line reaches the line's leading edge, and that is the left
     * only while the paragraph runs left to right. Taking `getLineLeft` for it regardless drew the
     * Arabic highlight mirrored — over the tail following the run rather than the run — which is the
     * bug the left-to-right case above fixes, surviving in the direction it wasn't tested in.
     */
    @Test
    @Config(sdk = [29])
    public fun rtlWrappedHighlight_coversTheRunAndNotWhatFollowsIt() {
        val view = labelView(ARABIC_HIGHLIGHT, highlightHex = HIGHLIGHT_HEX, cornerRadius = 0)
        measureAndLayout(view)

        val layout = view.layout
        val spanned = view.text as Spanned
        val span = spanned.getSpans(0, spanned.length, LineBackgroundSpan::class.java).first()
        val spanEnd = spanned.getSpanEnd(span)
        val line = layout.getLineForOffset(spanEnd)

        assertEquals(
            "the paragraph should be laid out right to left",
            Layout.DIR_RIGHT_TO_LEFT,
            layout.getParagraphDirection(line)
        )
        assertTrue(
            "the run should wrap",
            line > layout.getLineForOffset(spanned.getSpanStart(span))
        )

        // Right to left, so the run runs from the line's right edge leftward to where it ends, and
        // the tail after it takes the rest of the line.
        val runEndX = layout.getPrimaryHorizontal(spanEnd)
        val lineLeft = layout.getLineLeft(line)
        val lineRight = layout.getLineRight(line)
        assertTrue("the run should end inside the line", runEndX in (lineLeft + 1)..(lineRight - 1))

        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))

        // Just below the line's top edge: inside the background rect, clear of the glyphs.
        val y = view.extendedPaddingTop + layout.getLineTop(line) + 2

        assertEquals(
            "the run itself should be highlighted",
            HIGHLIGHT_COLOR,
            bitmap.getPixel(((runEndX + lineRight) / 2).toInt(), y)
        )
        assertNotEquals(
            "the tail after the run should not be highlighted",
            HIGHLIGHT_COLOR,
            bitmap.getPixel(((lineLeft + runEndX) / 2).toInt(), y)
        )
    }

    private fun assertNoLineOverruns(view: LabelView) {
        val layout = view.layout
        for (line in 0 until layout.lineCount) {
            // A fraction over is rounding, not an overrun.
            assertTrue(
                "line $line is ${layout.getLineWidth(line)}px wide in a ${layout.width}px layout",
                layout.getLineWidth(line) <= layout.width + 1f
            )
        }
    }

    private fun measureAndLayout(view: View, width: Int = 300) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    /** Auto height, so nothing truncates and the highlight is the only thing under test. */
    private fun labelView(
        text: String,
        highlightHex: String? = null,
        cornerRadius: Int = 4
    ): LabelView {
        val markdown = if (highlightHex == null) "" else """
            "markdown": {
                "appearance": {
                    "highlight": {
                        "corner_radius": $cornerRadius,
                        "color": { "default": { "hex": "$highlightHex", "alpha": 1 } }
                    }
                }
            },
        """.trimIndent()

        val info = ViewInfo.viewInfoFromJson(
            JsonValue.parseString(
                """
                {
                    "type": "label",
                    "text": "$text",
                    $markdown
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
            ItemProperties(Size("100%", "auto"))
        ) as LabelView
    }

    private companion object {
        const val HIGHLIGHT_HEX = "#FFE066"
        const val HIGHLIGHT_COLOR = 0xFFFFE066.toInt()

        /** Arabic, so the paragraph is laid out right to left with the run wrapping inside it. */
        const val ARABIC_HIGHLIGHT =
            "مرحبا بالعالم ==هذا نص مميز طويل بما يكفي لكي ينكسر إلى سطرين== ونهاية هنا."

        const val LONG_HIGHLIGHT =
            "A lead-in, then ==a highlighted run long enough that it cannot possibly sit on one " +
                "line by itself, so the layout has to be free to break inside it==, and a tail " +
                "after the highlight closes."
    }
}

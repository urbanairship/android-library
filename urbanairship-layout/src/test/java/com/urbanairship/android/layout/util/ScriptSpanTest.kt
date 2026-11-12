/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import android.text.Spanned
import android.text.TextPaint
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.widget.TextView
import com.urbanairship.android.layout.property.MarkdownOptions
import com.urbanairship.android.layout.property.TextAppearance
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
public class ScriptSpanTest {

    private val context = RuntimeEnvironment.getApplication()

    private fun render(markdown: String): TextView {
        val textView = TextView(context)
        LayoutUtils.applyLabel(
            textView = textView,
            textAppearance = TextAppearance.fromJson(
                JsonValue.parseString("""{ "font_size": 40, "color": { "default": { "type": "hex", "hex": "#000000" } } }""")
            ),
            markdownOptions = MarkdownOptions(JsonMap.EMPTY_MAP),
            text = markdown
        )
        return textView
    }

    /** Returns the paint the label draws [run] with, after every span over its first character. */
    private fun TextView.paintFor(run: String): TextPaint {
        val text = text as Spanned
        val index = text.indexOf(run)
        val paint = TextPaint(paint)
        text.getSpans(index, index + 1, ScriptSpan::class.java).forEach { it.updateDrawState(paint) }
        return paint
    }

    @Test
    public fun superscriptIsSmallerAndRaisedByTheLabelSize() {
        val paint = TextPaint().apply { textSize = 100f }
        ScriptSpan(100f, superscript = true).updateDrawState(paint)

        assertEquals(65f, paint.textSize, 0.01f)
        assertEquals(-40, paint.baselineShift)
    }

    @Test
    public fun subscriptIsSmallerAndLoweredByTheLabelSize() {
        val paint = TextPaint().apply { textSize = 100f }
        ScriptSpan(100f, superscript = false).updateMeasureState(paint)

        assertEquals(65f, paint.textSize, 0.01f)
        assertEquals(20, paint.baselineShift)
    }

    @Test
    public fun nestedScriptsAddShiftsWithoutShrinkingAgain() {
        val paint = TextPaint().apply { textSize = 100f }
        ScriptSpan(100f, superscript = true).updateDrawState(paint)
        ScriptSpan(100f, superscript = false).updateDrawState(paint)

        assertEquals(65f, paint.textSize, 0.01f)
        assertEquals(-20, paint.baselineShift)
    }

    @Test
    public fun labelDrawsSuperscriptFromItsOwnTextSize() {
        val label = render("E = mc^^2^^")
        val paint = label.paintFor("2")

        assertEquals(label.textSize * SCRIPT_FONT_SCALE, paint.textSize, 0.01f)
        assertEquals(-Math.round(label.textSize * SUPERSCRIPT_BASELINE_SCALE), paint.baselineShift)
    }

    @Test
    public fun labelDrawsSubscriptFromItsOwnTextSize() {
        val label = render("H,{2},O")
        val paint = label.paintFor("2")

        assertEquals(label.textSize * SCRIPT_FONT_SCALE, paint.textSize, 0.01f)
        assertEquals(Math.round(label.textSize * SUBSCRIPT_BASELINE_SCALE), paint.baselineShift)
    }

    @Test
    public fun labelDrawsNestedScriptsFromItsOwnTextSize() {
        val label = render("x^^a,{b},^^")
        val paint = label.paintFor("b")

        assertEquals(label.textSize * SCRIPT_FONT_SCALE, paint.textSize, 0.01f)
        assertEquals(
            -Math.round(label.textSize * SUPERSCRIPT_BASELINE_SCALE) + Math.round(label.textSize * SUBSCRIPT_BASELINE_SCALE),
            paint.baselineShift
        )
    }

    @Test
    public fun labelKeepsNoFrameworkScriptSpans() {
        val text = render("x^^a^^ y,{b},").text as Spanned

        assertEquals(0, text.getSpans(0, text.length, SuperscriptSpan::class.java).size)
        assertEquals(0, text.getSpans(0, text.length, SubscriptSpan::class.java).size)
    }
}

/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import com.urbanairship.android.layout.property.MarkdownOptions
import com.urbanairship.android.layout.property.NamedColors
import com.urbanairship.android.layout.property.NamedColorsContext
import com.urbanairship.android.layout.property.TextAppearance
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
public class MarkdownColorSpansTest {

    private val colors = NamedColors.EMPTY.scopedTo(
        JsonValue.parseString(
            """
            {
              "references": {
                "colors": {
                  "outer": { "default": { "type": "hex", "hex": "#0057FF", "alpha": 1 } },
                  "inner": { "default": { "type": "hex", "hex": "#D0021B", "alpha": 0.5 } }
                }
              }
            }
            """
        ).optMap()
    )

    private val context = NamedColorsContext.wrap(RuntimeEnvironment.getApplication(), colors)

    private val outerColor = 0xFF0057FF.toInt()
    private val innerColor = 0x7FD0021B.toInt()

    private fun render(markdown: String): Spanned {
        val textView = TextView(context)
        LayoutUtils.applyLabel(
            textView = textView,
            textAppearance = TextAppearance.fromJson(
                JsonValue.parseString("""{ "font_size": 14, "color": { "default": { "type": "hex", "hex": "#000000" } } }""")
            ),
            markdownOptions = MarkdownOptions(JsonMap.EMPTY_MAP),
            text = markdown
        )
        return textView.text as Spanned
    }

    private fun Spanned.textOf(span: Any): String =
        subSequence(getSpanStart(span), getSpanEnd(span)).toString()

    @Test
    public fun colorSpansGetTheSceneColorTheyName() {
        val spanned = render("[Sale]{color:outer} ends [today]{color:inner}")

        assertEquals("Sale ends today", spanned.toString())
        val spans = spanned.getSpans(0, spanned.length, ForegroundColorSpan::class.java)
        assertEquals(setOf("Sale" to outerColor, "today" to innerColor), spans.map { spanned.textOf(it) to it.foregroundColor }.toSet())
    }

    @Test
    public fun unknownNameHidesSyntaxWithoutColor() {
        val spanned = render("[Sale]{color:missing} ends")

        assertEquals("Sale ends", spanned.toString())
        assertTrue(spanned.getSpans(0, spanned.length, ForegroundColorSpan::class.java).isEmpty())
    }

    @Test
    public fun nestedSpanDrawsOverItsParent() {
        val spanned = render("[one [two]{color:inner} one]{color:outer}")

        // Later spans draw over earlier ones, so the inner span has to come after the outer.
        val spans = spanned.getSpans(0, spanned.length, ForegroundColorSpan::class.java)
        assertEquals(listOf("one two one" to outerColor, "two" to innerColor), spans.map { spanned.textOf(it) to it.foregroundColor })
    }

    @Test
    public fun linkInsideSpanDrawsOverTheSpanColor() {
        val spanned = render("[read the [docs](https://www.airship.com) today]{color:outer}")

        assertEquals("read the docs today", spanned.toString())
        val styles = spanned.getSpans(0, spanned.length, CharacterStyle::class.java).toList()
        val color = styles.filterIsInstance<ForegroundColorSpan>().single()
        val link = styles.filterIsInstance<ClickableSpan>().single()
        assertEquals("read the docs today", spanned.textOf(color))
        assertEquals("docs", spanned.textOf(link))
        assertTrue(styles.indexOf(link) > styles.indexOf(color))
    }

    @Test
    public fun sameRangeNestedSpanDrawsOverItsParent() {
        val spanned = render("[[x]{color:inner}]{color:outer}")
        val spans = spanned.getSpans(0, spanned.length, ForegroundColorSpan::class.java)
        assertEquals(innerColor, spans.last().foregroundColor)
    }
}

/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.core.text.HtmlCompat
import com.urbanairship.android.layout.property.MarkdownOptions
import com.urbanairship.android.layout.property.colorNames
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
public class MarkdownColorSpansTest {

    private val context = RuntimeEnvironment.getApplication()

    private val options = MarkdownOptions(
        JsonValue.parseString(
            """
            {
              "appearance": {
                "colors": {
                  "outer": { "default": { "type": "hex", "hex": "#0057FF", "alpha": 1 } },
                  "inner": { "default": { "type": "hex", "hex": "#D0021B", "alpha": 0.5 } }
                }
              }
            }
            """
        ).optMap()
    )

    private val outerColor = 0xFF0057FF.toInt()
    private val innerColor = 0x7FD0021B.toInt()

    private fun render(markdown: String): Spanned {
        val textView = TextView(context)
        val html = HtmlCompat.fromHtml(
            markdown.markdownToHtml(options.colorNames),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        textView.setHtml(context, html, options)
        return textView.text as Spanned
    }

    private fun Spanned.textOf(span: Any): String =
        subSequence(getSpanStart(span), getSpanEnd(span)).toString()

    @Test
    public fun colorSpansGetTheirPaletteColor() {
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
}

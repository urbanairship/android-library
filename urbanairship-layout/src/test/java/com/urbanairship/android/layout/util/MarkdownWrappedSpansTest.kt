/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.widget.TextView
import com.urbanairship.android.layout.property.MarkdownOptions
import com.urbanairship.android.layout.property.NamedColors
import com.urbanairship.android.layout.property.NamedColorsContext
import com.urbanairship.android.layout.property.TextAppearance
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
public class MarkdownWrappedSpansTest {

    private val colors = NamedColors.EMPTY.scopedTo(
        JsonValue.parseString(
            """{ "references": { "colors": { "brand": { "default": { "type": "hex", "hex": "#0057FF", "alpha": 1 } } } } }"""
        ).optMap()
    )

    private val context = NamedColorsContext.wrap(RuntimeEnvironment.getApplication(), colors)

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

    private fun Spanned.coveredBy(type: Class<*>): Set<String> =
        getSpans(0, length, type).map { subSequence(getSpanStart(it), getSpanEnd(it)).toString() }.toSet()

    @Test
    public fun strikethroughKeepsGoingPastAHighlight() {
        assertEquals(setOf("bb cc"), render("aa ~~==bb== cc~~").coveredBy(StrikethroughSpan::class.java))
    }

    @Test
    public fun boldAndItalicKeepGoingPastAHighlight() {
        assertEquals(setOf("bb cc"), render("aa **==bb== cc**").coveredBy(StyleSpan::class.java))
        assertEquals(setOf("bb cc"), render("aa _==bb== cc_").coveredBy(StyleSpan::class.java))
    }

    @Test
    public fun strikethroughKeepsGoingPastAColorSpan() {
        assertEquals(setOf("bb cc"), render("aa ~~[bb]{color:brand} cc~~").coveredBy(StrikethroughSpan::class.java))
    }

    @Test
    public fun separateHighlightsStayApartWhenTheTextStartsWithOne() {
        val spanned = render("==aa== bb ==cc==")
        val highlighted = spanned.getSpans(0, spanned.length, Any::class.java)
            .filter { it.javaClass.simpleName == "RoundedBackgroundSpan" }
            .map { spanned.subSequence(spanned.getSpanStart(it), spanned.getSpanEnd(it)).toString() }
        assertEquals(setOf("aa", "cc"), highlighted.toSet())
    }

    @Test
    public fun aColorSpanKeepsGoingPastAHighlight() {
        assertEquals(setOf("bb cc"), render("aa [==bb== cc]{color:brand}").coveredBy(ForegroundColorSpan::class.java))
    }
}

/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

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
public class LabelSpacesTest {

    private fun render(markdown: String): String {
        val textView = TextView(RuntimeEnvironment.getApplication())
        LayoutUtils.applyLabel(
            textView = textView,
            textAppearance = TextAppearance.fromJson(
                JsonValue.parseString("""{ "font_size": 14, "color": { "default": { "type": "hex", "hex": "#000000" } } }""")
            ),
            markdownOptions = MarkdownOptions(JsonMap.EMPTY_MAP),
            text = markdown
        )
        return textView.text.toString().trimEnd('\n').replace(' ', '~')
    }

    @Test
    public fun repeatedSpacesDrawAsTyped() {
        assertEquals("a~~ b", render("a   b"))
    }

    @Test
    public fun leadingAndTrailingSpacesDrawAsTyped() {
        assertEquals("~~ a", render("   a"))
        assertEquals("a~~", render("a  "))
    }

    @Test
    public fun spacesAroundALineBreakDrawAsTyped() {
        assertEquals("a~\n~b", render("a \n b"))
    }

    @Test
    public fun spacesAroundMarkdownDrawAsTyped() {
        assertEquals("a~ b c", render("a  **b** c"))
    }

    @Test
    public fun spacesAroundAHighlightDrawAsTyped() {
        assertEquals("~a~ b", render(" ==a==  b"))
    }

    @Test
    public fun singleSpacesAreUnchanged() {
        assertEquals("a b c", render("a b c"))
    }
}

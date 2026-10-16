/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class TextInputTextAppearanceTest {

    @Test
    public fun testAFieldWithNoAlignmentReadsFromItsStart() {
        assertEquals(TextAlignment.START, parse(alignment = null).alignment)
    }

    @Test
    public fun testAFieldKeepsTheAlignmentItStates() {
        assertEquals(TextAlignment.CENTER, parse(alignment = "center").alignment)
        assertEquals(TextAlignment.END, parse(alignment = "end").alignment)
    }

    @Test
    public fun testTheSmsLocalePickerStaysCentred() {
        val appearance = parse(alignment = null)
        assertEquals(TextAlignment.START, appearance.alignment)
        assertEquals(TextAlignment.CENTER, appearance.localePickerAppearance.alignment)
        assertEquals(TextAlignment.END, parse(alignment = "end").localePickerAppearance.alignment)
    }

    @Test
    public fun testALabelWithNoAlignmentStaysCentred() {
        assertEquals(TextAlignment.CENTER, TextAppearance.fromJson(json(alignment = null)).alignment)
    }

    private fun parse(alignment: String?) = TextInputTextAppearance.fromJson(json(alignment))

    private fun json(alignment: String?): JsonValue = JsonValue.parseString(
        """
        {
          "font_size": 12,
          ${alignment?.let { """"alignment": "$it",""" } ?: ""}
          "color": {"default": {"type": "hex", "hex": "#111111", "alpha": 1}}
        }
        """.trimIndent()
    )
}

/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import androidx.annotation.Dimension
import androidx.annotation.IntRange
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalList

public open class TextAppearance {

    public val color: Color

    @JvmField
    @Dimension(unit = Dimension.Companion.DP)
    public val fontSize: Int

    @JvmField
    public val alignment: TextAlignment

    @JvmField
    public val textStyles: List<TextStyle>

    @JvmField @IntRange(from = 0, to = 900)
    public val fontWeight: Int

    @JvmField
    public val fontFamilies: List<String>

    protected constructor(textAppearance: TextAppearance) {
        this.color = textAppearance.color
        this.fontSize = textAppearance.fontSize
        this.alignment = textAppearance.alignment
        this.textStyles = textAppearance.textStyles
        this.fontWeight = textAppearance.fontWeight
        this.fontFamilies = textAppearance.fontFamilies
    }

    public constructor(
        color: Color,
        fontSize: Int,
        alignment: TextAlignment,
        textStyles: List<TextStyle>,
        fontWeight: Int,
        fontFamilies: List<String>
    ) {
        this.color = color
        this.fontSize = fontSize
        this.alignment = alignment
        this.textStyles = textStyles
        this.fontWeight = fontWeight
        this.fontFamilies = fontFamilies
    }

    public companion object {
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_COLOR = "color"
        private const val KEY_ALIGNMENT = "alignment"
        private const val KEY_STYLES = "styles"
        private const val KEY_FONT_WEIGHT = "font_weight"
        private const val KEY_FONT_FAMILIES = "font_families"

        @JvmStatic
        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): TextAppearance {
            val content = json.requireMap()

            return TextAppearance(
                color = Color.fromJson(content.require(KEY_COLOR)),
                fontSize = content.optionalField(KEY_FONT_SIZE) ?: 14,
                alignment = content[KEY_ALIGNMENT]?.let(TextAlignment::from) ?: TextAlignment.CENTER,
                textStyles = content.optionalList(KEY_STYLES)?.map(TextStyle::from) ?: emptyList(),
                fontWeight = content.optionalField(KEY_FONT_WEIGHT) ?: 0,
                fontFamilies = content.optionalList(KEY_FONT_FAMILIES)?.mapNotNull { it.string } ?: emptyList()
            )
        }
    }
}

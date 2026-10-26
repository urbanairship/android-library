/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalList
import com.urbanairship.json.optionalMap
import com.urbanairship.json.requireField

public class Color private constructor(
    private val defaultColor: Int,
    private val selectors: List<ColorSelector>,
    /** The name of the color this stands in for, looked up in [context][resolve]'s named colors. */
    private val referenceName: String?
) {

    public constructor(defaultColor: Int, selectors: List<ColorSelector>) : this(defaultColor, selectors, null)

    /**
     * Returns the first selector's color that [context]'s environment matches, else the default.
     *
     * A reference resolves the color it names in [context]'s layout; see [NamedColorsContext].
     */
    @ColorInt
    public fun resolve(context: Context): Int {
        if (referenceName != null) return NamedColorsContext.colorNamed(context, referenceName).resolve(context)
        val environment = SelectorEnvironment.from(context)
        return selectors.firstOrNull { it.conditions.matches(environment) }?.color ?: defaultColor
    }

    public companion object {

        @ColorInt
        public const val TRANSPARENT: Int = Color.TRANSPARENT

        @ColorInt
        public const val WHITE: Int = Color.WHITE

        @ColorInt
        public const val BLACK: Int = Color.BLACK

        private const val KEY_DEFAULT = "default"
        private const val KEY_SELECTORS = "selectors"

        private const val TYPE_COLOR_REFERENCE = "color_reference"

        /** Opaque black, which a reference to a name its layout doesn't define draws. */
        internal val MISSING: com.urbanairship.android.layout.property.Color = Color(BLACK, emptyList())

        @JvmStatic
        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): com.urbanairship.android.layout.property.Color {
            val content = json.requireMap()
            if (content.opt("type").optString() == TYPE_COLOR_REFERENCE) {
                return Color(BLACK, emptyList(), referenceName = content.requireField("name"))
            }
            return valueFromJson(json)
        }

        /** Parses a color given in place, never a reference. */
        @Throws(JsonException::class)
        internal fun valueFromJson(json: JsonValue): com.urbanairship.android.layout.property.Color {
            val content = json.requireMap()

            @ColorInt val defaultColor = HexColor.fromJson(content.optionalMap(KEY_DEFAULT))
            if (defaultColor == null) {
                throw JsonException("Failed to parse color. 'default' may not be null! json = $json")
            }

            val selectorsJson = content.opt(KEY_SELECTORS).optList()

            return Color(
                defaultColor = defaultColor,
                selectors = ColorSelector.fromJsonList(selectorsJson)
            )
        }

        @JvmStatic
        @Throws(JsonException::class)
        public fun fromJsonField(
            json: JsonMap?,
            fieldName: String
        ): com.urbanairship.android.layout.property.Color? {
            val source = json?.get(fieldName) ?: return null
            return fromJson(source)
        }

        @JvmStatic
        public fun alpha(@ColorInt color: Int): Float {
            return Color.alpha(color).toFloat()
        }
    }
}

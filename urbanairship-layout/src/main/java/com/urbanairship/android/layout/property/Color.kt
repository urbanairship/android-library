/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalList
import com.urbanairship.json.optionalMap

public class Color public constructor(
    private val defaultColor: Int,
    private val selectors: List<ColorSelector>
) {

    @ColorInt
    public fun resolve(context: Context): Int {
        // Look for a selector that matches the current UI mode.
        val isDarkMode = ResourceUtils.isUiModeNight(context)
        return selectors
            .firstOrNull { it.isDarkMode == isDarkMode }
            ?.color
            ?: defaultColor
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

        @JvmStatic
        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): com.urbanairship.android.layout.property.Color {
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

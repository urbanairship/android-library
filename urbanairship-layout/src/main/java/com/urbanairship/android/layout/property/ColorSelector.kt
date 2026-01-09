/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import androidx.annotation.ColorInt
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalMap

public class ColorSelector public constructor(
    public val platform: Platform?,
    public val isDarkMode: Boolean,
    @field:ColorInt public val color: Int
) {

    public companion object {
        private const val KEY_PLATFORM = "platform"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_COLOR = "color"

        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): ColorSelector {
            val content = json.requireMap()

            @ColorInt val color = HexColor.fromJson(content.optionalMap(KEY_COLOR))
            if (color == null) {
                throw JsonException("Failed to parse color selector. 'color' may not be null! json = '$json'")
            }

            return ColorSelector(
                platform = content[KEY_PLATFORM]?.let(Platform::from),
                isDarkMode = content.optionalField(KEY_DARK_MODE) ?: false,
                color = color
            )
        }

        @Throws(JsonException::class)
        public fun fromJsonList(json: JsonList): List<ColorSelector> {
            return json
                .map(::fromJson)
                // Ignore any non-android selectors.
                .filter { it.platform == null || it.platform == Platform.ANDROID }
        }
    }
}

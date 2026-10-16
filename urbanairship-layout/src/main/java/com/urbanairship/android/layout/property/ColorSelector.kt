/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import androidx.annotation.ColorInt
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalMap

public class ColorSelector public constructor(
    /** What the environment has to match for [color] to apply. */
    public val conditions: SelectorConditions,
    @field:ColorInt public val color: Int
) {

    public companion object {
        private const val KEY_COLOR = "color"

        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): ColorSelector {
            val content = json.requireMap()

            @ColorInt val color = HexColor.fromJson(content.optionalMap(KEY_COLOR))
            if (color == null) {
                throw JsonException("Failed to parse color selector. 'color' may not be null! json = '$json'")
            }

            return ColorSelector(conditions = SelectorConditions.fromJson(content), color = color)
        }

        @Throws(JsonException::class)
        public fun fromJsonList(json: JsonList): List<ColorSelector> {
            return json.map(::fromJson)
        }
    }
}

/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

public class TextInputTextAppearance public constructor(
    textAppearance: TextAppearance,
    @JvmField public val hintColor: Color?
) : TextAppearance(textAppearance) {

    public companion object {
        private const val KEY_PLACEHOLDER_COLOR = "place_holder_color"

        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): TextInputTextAppearance {
            val content = json.requireMap()

            return TextInputTextAppearance(
                textAppearance = TextAppearance.fromJson(json),
                hintColor = content[KEY_PLACEHOLDER_COLOR]?.let(Color::fromJson)
            )
        }
    }
}

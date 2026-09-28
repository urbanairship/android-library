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
        private const val KEY_ALIGNMENT = "alignment"

        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): TextInputTextAppearance {
            val content = json.requireMap()

            // A field left to the default reads from its start, as on web and iOS. Centred, a
            // single-line field's hint sits in the middle of its unbounded layout, off-screen.
            val parsed = TextAppearance.fromJson(json)
            return TextInputTextAppearance(
                textAppearance = if (content.containsKey(KEY_ALIGNMENT)) parsed else parsed.withAlignment(TextAlignment.START),
                hintColor = content[KEY_PLACEHOLDER_COLOR]?.let(Color::fromJson)
            )
        }
    }
}

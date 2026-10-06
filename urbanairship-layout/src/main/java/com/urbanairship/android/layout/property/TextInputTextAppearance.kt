/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

public class TextInputTextAppearance public constructor(
    textAppearance: TextAppearance,
    @JvmField public val hintColor: Color?
) : TextAppearance(textAppearance) {

    /** The appearance for an SMS field's locale picker, which is centred where none is stated. */
    internal var localePickerAppearance: TextAppearance = this
        private set

    public companion object {
        private const val KEY_PLACEHOLDER_COLOR = "place_holder_color"

        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): TextInputTextAppearance {
            val content = json.requireMap()

            // A field left to the default reads from its start, as on web and iOS. Centred, a
            // single-line field's hint sits in the middle of its unbounded layout, off-screen.
            return TextInputTextAppearance(
                textAppearance = TextAppearance.fromJson(json, TextAlignment.START),
                hintColor = content[KEY_PLACEHOLDER_COLOR]?.let(Color::fromJson)
            ).apply {
                // The SMS locale picker keeps the centred default, as web's does.
                localePickerAppearance = TextAppearance.fromJson(json)
            }
        }
    }
}

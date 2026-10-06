/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

/**
 * How a [Background] layer composites with whatever is painted below it.
 *
 * Capped at the modes CSS, SwiftUI, and a Porter-Duff `Xfermode` can all express without a custom
 * shader -- `hue`/`saturation`/`color`/`luminosity` are deliberately excluded so this set behaves
 * identically on every platform.
 */
internal enum class BlendMode(private val value: String) {
    NORMAL("normal"),
    MULTIPLY("multiply"),
    SCREEN("screen"),
    OVERLAY("overlay"),
    DARKEN("darken"),
    LIGHTEN("lighten"),
    COLOR_DODGE("color_dodge"),
    COLOR_BURN("color_burn"),
    HARD_LIGHT("hard_light"),
    SOFT_LIGHT("soft_light"),
    DIFFERENCE("difference"),
    EXCLUSION("exclusion");

    companion object {

        @Throws(JsonException::class)
        fun from(value: JsonValue): BlendMode {
            val content = value.requireString().lowercase()
            return entries.firstOrNull { it.value == content }
                ?: throw JsonException("Unknown BlendMode value: $value")
        }
    }
}

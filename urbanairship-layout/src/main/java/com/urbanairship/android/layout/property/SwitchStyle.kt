/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.requireMap

public class SwitchStyle public constructor(
    @JvmField public val onColor: Color,
    @JvmField public val offColor: Color
) : ToggleStyle(ToggleType.SWITCH) {

    public companion object {

        private const val KEY_TOGGLE_COLORS = "toggle_colors"
        private const val KEY_ON = "on"
        private const val KEY_OFF = "off"

        @JvmStatic
        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): SwitchStyle {
            val content = json.requireMap()
            val colors = content.requireMap(KEY_TOGGLE_COLORS)

            return SwitchStyle(
                onColor = Color.fromJson(colors.require(KEY_ON)),
                offColor = Color.fromJson(colors.require(KEY_OFF))
            )
        }
    }
}

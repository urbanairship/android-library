/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

public abstract class ToggleStyle internal constructor(
    public val type: ToggleType
) {

    public companion object {
        private const val KEY_TYPE = "type"

        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): ToggleStyle {
            val content = json.requireMap()
            val type = ToggleType.from(content.require(KEY_TYPE))
            return when (type) {
                ToggleType.SWITCH -> SwitchStyle.fromJson(json)
                ToggleType.CHECKBOX -> CheckboxStyle.fromJson(json)
            }
        }
    }
}

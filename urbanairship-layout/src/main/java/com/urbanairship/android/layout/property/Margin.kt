/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField

public class Margin public constructor(
    @JvmField public val top: Int,
    @JvmField public val bottom: Int,
    @JvmField public val start: Int,
    @JvmField public val end: Int
) {

    public companion object {

        private const val KEY_TOP = "top"
        private const val KEY_BOTTOM = "bottom"
        private const val KEY_START = "start"
        private const val KEY_END = "end"

        public var NONE: Margin = Margin(0, 0, 0, 0)

        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): Margin {
            val content = json.requireMap()

            return Margin(
                top = content.opt(KEY_TOP).getInt(0),
                bottom = content.opt(KEY_BOTTOM).getInt(0),
                start = content.opt(KEY_START).getInt(0),
                end = content.opt(KEY_END).getInt(0)
            )
        }
    }
}

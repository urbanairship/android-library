/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import android.view.Gravity
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

public class Position public constructor(
    @JvmField public val horizontal: HorizontalPosition,
    @JvmField public val vertical: VerticalPosition
) {

    public fun getGravity(): Int {
        return Gravity.CENTER or horizontal.gravity or vertical.gravity
    }

    public companion object {
        private const val KEY_HORIZONTAL = "horizontal"
        private const val KEY_VERTICAL = "vertical"

        public val CENTER: Position = Position(HorizontalPosition.CENTER, VerticalPosition.CENTER)

        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): Position {
            val content = json.requireMap()

            return Position(
                horizontal = HorizontalPosition.from(content.require(KEY_HORIZONTAL)),
                vertical = VerticalPosition.from(content.require(KEY_VERTICAL))
            )
        }
    }
}

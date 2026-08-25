/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

public class EdgePosition
@Throws(JsonException::class)
public constructor(
    @JvmField public val horizontal: HorizontalPosition,
    @JvmField public val vertical: VerticalPosition
) : JsonSerializable {

    init {
        if (horizontal == HorizontalPosition.CENTER && vertical == VerticalPosition.CENTER) {
            throw JsonException(
                "EdgePosition cannot have both horizontal and vertical set to center."
            )
        }
    }

    public fun asPosition(): Position {
        return Position(horizontal, vertical)
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        KEY_HORIZONTAL to horizontal.toString(),
        KEY_VERTICAL to vertical.toString()
    ).toJsonValue()

    public companion object {
        private const val KEY_HORIZONTAL = "horizontal"
        private const val KEY_VERTICAL = "vertical"

        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): EdgePosition {
            val content = json.requireMap()

            return EdgePosition(
                horizontal = HorizontalPosition.from(content.require(KEY_HORIZONTAL)),
                vertical = VerticalPosition.from(content.require(KEY_VERTICAL))
            )
        }
    }
}

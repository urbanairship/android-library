/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

public class CornerPosition public constructor(
    @JvmField public val horizontal: HorizontalEdge,
    @JvmField public val vertical: VerticalEdge
) : JsonSerializable {

    public fun asPosition(): Position {
        return Position(horizontal.baseType, vertical.baseType)
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        KEY_HORIZONTAL to horizontal.toString(),
        KEY_VERTICAL to vertical.toString()
    ).toJsonValue()

    public companion object {
        private const val KEY_HORIZONTAL = "horizontal"
        private const val KEY_VERTICAL = "vertical"

        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): CornerPosition {
            val content = json.requireMap()

            return CornerPosition(
                horizontal = HorizontalEdge.from(content.require(KEY_HORIZONTAL)),
                vertical = VerticalEdge.from(content.require(KEY_VERTICAL))
            )
        }
    }
}

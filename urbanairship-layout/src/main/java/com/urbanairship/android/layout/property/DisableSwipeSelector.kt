/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireMap

internal data class DisableSwipeSelector(
    val predicate: JsonPredicate? = null,
    val direction: Direction
): JsonSerializable {
    enum class Direction(val json: String): JsonSerializable {
        HORIZONTAL("horizontal");

        override fun toJsonValue(): JsonValue = JsonValue.wrap(json)

        companion object {
            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): Direction {
                val content = value.requireString()
                return entries.firstOrNull { it.json == content }
                    ?: throw JsonException("Invalid direction value for type $content")
            }
        }
    }

    companion object {
        private const val PREDICATE = "when_state_matches"
        private const val DIRECTIONS = "directions"
        private const val TYPE = "type"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): DisableSwipeSelector {
            val content = value.requireMap()

            return DisableSwipeSelector(
                predicate = content[PREDICATE]?.let(JsonPredicate::parse),
                direction = content.requireMap(DIRECTIONS).require(TYPE).let(Direction::fromJson)
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        PREDICATE to predicate,
        DIRECTIONS to jsonMapOf(TYPE to direction)
    ).toJsonValue()
}

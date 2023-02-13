package com.urbanairship.android.layout.property

import com.urbanairship.android.layout.util.requireField
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap

internal data class EventHandler(
    val type: Type,
    val actions: List<StateAction>
) {
    constructor(json: JsonMap) : this(
        type = Type.from(json.requireField<String>("type")),
        actions = json.requireField<JsonList>("state_actions").map {
            StateAction.fromJson(it.optMap())
        }
    )

    internal enum class Type(val value: String) {
        TAP("tap"),
        FORM_INPUT("form_input");

        companion object {
            fun from(value: String): Type {
                return Type.values().firstOrNull { it.value == value }
                    ?: throw JsonException("Unknown EventHandler type: '$value'")
            }
        }
    }
}

internal fun List<EventHandler>?.hasTapHandler(): Boolean =
    orEmpty().any { it.type == EventHandler.Type.TAP }

internal fun List<EventHandler>?.hasFormInputHandler(): Boolean =
    orEmpty().any { it.type == EventHandler.Type.FORM_INPUT }

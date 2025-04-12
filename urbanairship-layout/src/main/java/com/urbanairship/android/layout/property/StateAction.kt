package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.requireField
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal sealed class StateAction(val type: Type) {

    object ClearState : StateAction(Type.CLEAR_STATE)

    data class SetState(
        val key: String,
        val value: JsonValue?,
        val ttl: Duration? = null
    ) : StateAction(Type.SET_STATE) {
        init {
            if (value?.isJsonList == true || value?.isJsonMap == true) {
                throw JsonException("State value must be a String, Number, or Boolean!")
            }
        }
    }
    data class SetFormValue(val key: String) : StateAction(Type.SET_FORM_VALUE_STATE)

    internal enum class Type(val value: String) {
        CLEAR_STATE("clear"),
        SET_STATE("set"),
        SET_FORM_VALUE_STATE("set_form_value");

        companion object {
            fun from(value: String): Type {
                return entries.firstOrNull { it.value == value }
                    ?: throw JsonException("Unknown StateAction type: '$value'")
            }
        }
    }

    companion object {

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): StateAction {
            val content = value.requireMap()
            return fromJson(content)
        }

        fun fromJson(json: JsonMap): StateAction {
            return when (Type.from(json.requireField("type"))) {
                Type.CLEAR_STATE -> ClearState
                Type.SET_STATE -> SetState(
                    key = json.requireField("key"),
                    value = json.get("value"),
                    ttl = json.get("ttl_seconds")?.getLong(0)?.seconds
                )
                Type.SET_FORM_VALUE_STATE -> SetFormValue(
                    key = json.requireField("key")
                )
            }
        }
    }
}

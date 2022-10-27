package com.urbanairship.android.layout.property

import com.urbanairship.android.layout.util.requireField
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

internal sealed class StateAction(val type: Type) {

    object ClearState : StateAction(Type.CLEAR_STATE)
    data class SetState(
        val key: String,
        val value: JsonValue?
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
                return values().firstOrNull { it.value == value }
                    ?: throw JsonException("Unknown StateAction type: '$value'")
            }
        }
    }

    companion object {
        fun fromJson(json: JsonMap): StateAction {
            return when (Type.from(json.requireField("type"))) {
                Type.CLEAR_STATE -> ClearState
                Type.SET_STATE -> SetState(
                    key = json.requireField("key"),
                    value = json.get("value")
                )
                Type.SET_FORM_VALUE_STATE -> SetFormValue(
                    key = json.requireField("key")
                )
            }
        }
    }
}

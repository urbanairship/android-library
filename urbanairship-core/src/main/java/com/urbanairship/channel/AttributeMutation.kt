/* Copyright Airship and Contributors */
package com.urbanairship.channel

import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import com.urbanairship.util.DateUtils
import java.util.Objects

/**
 * A model defining attribute mutations.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AttributeMutation internal constructor(
    public val action: String,
    public val name: String,
    public val value: JsonValue?,
    public val timestamp: String?
) : JsonSerializable {

    override fun toJsonValue(): JsonValue = jsonMapOf(
        ATTRIBUTE_ACTION_KEY to action,
        ATTRIBUTE_NAME_KEY to name,
        ATTRIBUTE_VALUE_KEY to value,
        ATTRIBUTE_TIMESTAMP_KEY to timestamp
    ).toJsonValue()

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false

        val mutation = o as AttributeMutation

        if (action != mutation.action) return false
        if (name != mutation.name) return false
        if (value != mutation.value) return false
        return timestamp == mutation.timestamp
    }

    override fun hashCode(): Int {
        return Objects.hash(action, name, value, timestamp)
    }

    override fun toString(): String {
        return "AttributeMutation{action='$action', name='$name', value=$value, timestamp='$timestamp'}"
    }

    internal companion object {

        const val ATTRIBUTE_ACTION_REMOVE: String = "remove"
        const val ATTRIBUTE_ACTION_SET: String = "set"

        const val ATTRIBUTE_NAME_KEY: String = "key"
        const val ATTRIBUTE_VALUE_KEY: String = "value"
        const val ATTRIBUTE_ACTION_KEY: String = "action"
        const val ATTRIBUTE_TIMESTAMP_KEY: String = "timestamp"

        /**
         * Creates a mutation to set a string attribute.
         *
         * @param key The string attribute key.
         * @param jsonValue The json value.
         * @param timestamp The timestamp in milliseconds.
         * @return The attribute mutation.
         */
        fun newSetAttributeMutation(key: String, jsonValue: JsonValue, timestamp: Long): AttributeMutation {
            require(!jsonValue.isNull) { "Invalid attribute value: $jsonValue" }

            return AttributeMutation(
                ATTRIBUTE_ACTION_SET, key, jsonValue, DateUtils.createIso8601TimeStamp(timestamp)
            )
        }

        /**
         * Creates a mutation to remove a string attribute.
         *
         * @param key The string attribute key.
         * @param timestamp The timestamp in milliseconds.
         * @return The attribute mutation.
         */
        fun newRemoveAttributeMutation(key: String, timestamp: Long): AttributeMutation {
            return AttributeMutation(
                ATTRIBUTE_ACTION_REMOVE, key, null, DateUtils.createIso8601TimeStamp(timestamp)
            )
        }

        @Throws(JsonException::class)
        fun fromJsonValue(jsonValue: JsonValue): AttributeMutation {
            val mutation = jsonValue.requireMap()

            val value = mutation[ATTRIBUTE_VALUE_KEY]
            value?.let {
                if (!isValidValue(it)) {
                    throw JsonException("Invalid attribute mutation value: $it")
                }
            }

            return AttributeMutation(
                action = mutation.requireField(ATTRIBUTE_ACTION_KEY),
                name = mutation.requireField(ATTRIBUTE_NAME_KEY),
                value = value,
                timestamp = mutation.optionalField(ATTRIBUTE_TIMESTAMP_KEY)
            )
        }

        fun fromJsonList(jsonList: JsonList): List<AttributeMutation> {

            return jsonList.mapNotNull {
                try {
                    fromJsonValue(it)
                } catch (e: JsonException) {
                    UALog.e(e, "Invalid attribute mutation.")
                    null
                }
            }
        }

        /**
         * Collapses a collection of mutation payloads to a single mutation payload.
         *
         * @param mutations a list of attribute mutation instances to collapse.
         * @return An attribute mutations instance.
         */
        fun collapseMutations(mutations: List<AttributeMutation>): List<AttributeMutation> {
            val result = mutableListOf<AttributeMutation>()
            val mutationNames = mutableSetOf<String>()

            mutations
                .reversed()
                .forEach { mutation ->
                    if (mutationNames.contains(mutation.name)) {
                        return@forEach
                    }

                    result.add(mutation)
                    mutationNames.add(mutation.name)
                }

            return result.reversed()
        }

        private fun isValidValue(jsonValue: JsonValue): Boolean {
            return !(jsonValue.isNull || jsonValue.isJsonList || jsonValue.isBoolean)
        }
    }
}

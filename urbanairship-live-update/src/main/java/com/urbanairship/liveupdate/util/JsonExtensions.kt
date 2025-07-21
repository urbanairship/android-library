/* Copyright Airship and Contributors */

package com.urbanairship.liveupdate.util

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

@Throws(JsonException::class)
internal fun jsonMapOf(vararg fields: Pair<String, *>): JsonMap =
    JsonMap.newBuilder().apply {
        for ((k, v) in fields) {
            put(k, JsonValue.wrap(v))
        }
    }.build()

@Throws(JsonException::class)
internal fun jsonListOf(vararg values: Any): JsonList = JsonList(values.map(JsonValue::wrap))

internal fun List<JsonSerializable>.toJsonList(): JsonList = JsonList(this.map { it.toJsonValue() })

internal val JsonList.isNotEmpty: Boolean
    get() = !isEmpty

internal fun Map<String, JsonValue>.toJsonMap(): JsonMap = JsonMap(this)

/**
 * Gets the field with the given [key] from the [JsonMap], ensuring it is non-null.
 *
 * @throws JsonException if an invalid type is specified, or if the field is `null` or missing.
 */
@Throws(JsonException::class)
internal inline fun <reified T> JsonMap.requireField(key: String): T {
    val field = get(key) ?: throw JsonException("Missing required field: '$key'")
    return when (T::class) {
        String::class -> field.optString() as T
        Boolean::class -> field.getBoolean(false) as T
        Long::class -> field.getLong(0) as T
        Double::class -> field.getDouble(0.0) as T
        Integer::class -> field.getInt(0) as T
        JsonList::class -> field.optList() as T
        JsonMap::class -> field.optMap() as T
        JsonValue::class -> field.toJsonValue() as T
        else -> throw JsonException("Invalid type '${T::class.java.simpleName}' for field '$key'")
    }
}

/**
 * Gets the field with the given [key] from the [JsonMap], or `null` if not defined.
 *
 * @throws JsonException if an invalid type is specified.
 */
@Throws(JsonException::class)
internal inline fun <reified T> JsonMap.optionalField(key: String): T? {
    val field = get(key) ?: return null
    return when (T::class) {
        String::class -> field.optString() as T
        Boolean::class -> field.getBoolean(false) as T
        Long::class -> field.getLong(0) as T
        Double::class -> field.getDouble(0.0) as T
        Integer::class -> field.getInt(0) as T
        JsonList::class -> field.optList() as T
        JsonMap::class -> field.optMap() as T
        JsonValue::class -> field.toJsonValue() as T
        else -> throw JsonException("Invalid type '${T::class.java.simpleName}' for field '$key'")
    }
}

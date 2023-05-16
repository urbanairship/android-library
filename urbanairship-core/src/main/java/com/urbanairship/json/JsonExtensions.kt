package com.urbanairship.json

import com.urbanairship.Logger

public fun jsonMapOf(vararg fields: Pair<String, *>): JsonMap =
    JsonMap.newBuilder().apply {
        for ((k, v) in fields) {
            put(k, JsonValue.wrap(v))
        }
    }.build()

public inline fun <T, R> R.tryParse(logError: Boolean = false, parser: (R) -> T): T? where R : JsonSerializable {
    return try {
        parser(this)
    } catch (e: JsonException) {
        if (logError) {
            Logger.error("Failed to parse json", e)
        }
        null
    }
}

public fun jsonListOf(vararg values: Any): JsonList = JsonList(values.map(JsonValue::wrap))

public fun <T> List<T>.toJsonList(): JsonList where T : JsonSerializable =
    JsonList(this.map { it.toJsonValue() })

public fun <T> Map<String, T?>.toJsonMap(): JsonMap where T : JsonSerializable =
    JsonMap(this.mapValues { it.value?.toJsonValue() ?: JsonValue.NULL })

/**
 * Gets the field with the given [key] from the [JsonMap], ensuring it is non-null.
 *
 * @throws JsonException if an invalid type is specified, or if the field is `null` or missing.
 */
public inline fun <reified T> JsonMap.requireField(key: String): T {
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
public inline fun <reified T> JsonMap.optionalField(key: String): T? {
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

/**
 * Gets the field with the given [key] from the [JsonMap] and convert it using [builder] function,
 * or `null` if not defined.
 */
internal inline fun <reified T> JsonMap.optionalFieldConverted(key: String, builder: (String) -> T?): T? {
    val result = optionalField<String>(key)?.let(builder)
    if (result == null) {
        Logger.e { "Failed to parse ${T::class.simpleName} from $key" }
    }
    return result
}

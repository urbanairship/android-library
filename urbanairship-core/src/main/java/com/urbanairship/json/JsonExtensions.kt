package com.urbanairship.json

import com.urbanairship.UALog
import com.urbanairship.util.DateUtils
import java.text.ParseException

@Throws(JsonException::class)
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
            UALog.e("Failed to parse json", e)
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
@Throws(JsonException::class)
public inline fun <reified T> JsonMap.requireField(key: String): T {
    val field = get(key) ?: throw JsonException("Missing required field: '$key'")
    return when (T::class) {
        String::class -> field.optString() as T
        Boolean::class -> field.getBoolean(false) as T
        Long::class -> field.getLong(0) as T
        ULong::class -> field.getLong(0).toULong() as T
        Double::class -> field.getDouble(0.0) as T
        Float::class -> field.getFloat(0f) as T
        Integer::class -> field.getInt(0) as T
        UInt::class -> field.getInt(0).toUInt() as T
        JsonList::class -> field.optList() as T
        JsonMap::class -> field.optMap() as T
        JsonValue::class -> field.toJsonValue() as T
        else -> throw JsonException("Invalid type '${T::class.java.simpleName}' for field '$key'")
    }
}

@Throws(JsonException::class)
public fun JsonMap.extend(vararg fields: Pair<String, *>): JsonMap {
    return JsonMap.newBuilder().putAll(this)
        .apply {
            for ((k, v) in fields) {
                put(k, JsonValue.wrap(v))
            }
        }.build()
}

/**
 * Gets the field with the given [key] from the [JsonMap], or `null` if not defined.
 *
 * @throws JsonException if an invalid type is specified.
 */
@Throws(JsonException::class)
public inline fun <reified T> JsonMap.optionalField(key: String): T? {
    val field = get(key) ?: return null
    return when (T::class) {
        String::class -> field.optString() as T
        Boolean::class -> field.getBoolean(false) as T
        Long::class -> field.getLong(0) as T
        ULong::class -> field.getLong(0).toULong() as T
        Double::class -> field.getDouble(0.0) as T
        Float::class -> field.getFloat(0f) as T
        Integer::class -> field.getInt(0) as T
        UInt::class -> field.getInt(0).toUInt() as T
        JsonList::class -> field.optList() as T
        JsonMap::class -> field.optMap() as T
        JsonValue::class -> field.toJsonValue() as T
        else -> throw JsonException("Invalid type '${T::class.java.simpleName}' for field '$key'")
    }
}


/**
 * Gets the field with the given [key] and parses it as a ISO date string.
 *
 * @throws JsonException if the value is not a valid date string.
 */
@Throws(JsonException::class)
public fun JsonMap.isoDateAsMilliseconds(key: String): Long? {
    return try {
        optionalField<String>(key)?.let {
            DateUtils.parseIso8601(it)
        }
    } catch (e: Exception) {
        throw JsonException("Unable to parse value as date: ${get(key)}", e)
    }
}

/**
 * Gets a map with the given [key] from the [JsonMap].
 *
 * @throws JsonException if the field is undefined or is not a valid [JsonMap].
 */
@Throws(JsonException::class)
public fun JsonMap.requireMap(key: String): JsonMap {
    return requireField(key)
}

/**
 * Gets a list with the given [key] from the [JsonMap].
 *
 * @throws JsonException if the field is undefined or is not a valid [JsonList].
 */
@Throws(JsonException::class)
public fun JsonMap.requireList(key: String): JsonList {
    return requireField(key)
}


/**
 * Gets a map with the given [key] from the [JsonMap], or an `null` if not defined.
 *
 * @throws JsonException if the field is undefined or is not a valid [JsonMap].
 */
@Throws(JsonException::class)
public fun JsonMap.optionalMap(key: String): JsonMap? {
    return optionalField<JsonMap>(key)
}

/**
 * Gets a list with the given [key] from the [JsonMap], or an `null` if not defined.
 *
 * @throws JsonException if the field is undefined or is not a valid [JsonList].
 */
@Throws(JsonException::class)
public fun JsonMap.optionalList(key: String): JsonList? {
    return optionalField<JsonList>(key)
}

/**
 * Gets the field with the given [key] from the [JsonMap] and convert it using [builder] function,
 * or `null` if not defined.
 */
@Throws(JsonException::class)
internal inline fun <reified T> JsonMap.optionalFieldConverted(key: String, builder: (String) -> T?): T? {
    val result = optionalField<String>(key)?.let(builder)
    if (result == null) {
        UALog.e { "Failed to parse ${T::class.simpleName} from $key" }
    }
    return result
}

/** Convenience method to create an empty [JsonMap]. */
public fun emptyJsonMap(): JsonMap = JsonMap.EMPTY_MAP

/** Convenience method to create an empty [JsonList]. */
public fun emptyJsonList(): JsonList = JsonList.EMPTY_LIST

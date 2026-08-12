package com.urbanairship.json

import com.urbanairship.UALog
import com.urbanairship.util.DateUtils
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

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
            UALog.e(e) { "Failed to parse json" }
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
        CharSequence::class -> field.optString() as T
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
        Instant::class -> field.requireEpochMillis(key) as T
        else -> throw JsonException("Invalid type '${T::class.java.simpleName}' for field '$key'")
    }
}

/**
 * Reads a [JsonValue] holding epoch milliseconds as an [Instant].
 *
 * This is the representation used for locally persisted timestamps. For ISO 8601 strings
 * — the representation used by server payloads — use [JsonMap.isoDateAsInstant] instead.
 *
 * @throws JsonException if the value is not a number.
 */
@Throws(JsonException::class)
public fun JsonValue.requireEpochMillis(key: String? = null): Instant {
    if (!isNumber) {
        val label = key?.let { "field '$it'" } ?: "value"
        throw JsonException("Unable to parse $label as epoch milliseconds: $this")
    }
    return Instant.ofEpochMilli(getLong(0))
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
        CharSequence::class -> field.optString() as T
        Boolean::class -> field.getBoolean(false) as T
        Long::class -> field.getLong(0) as T
        ULong::class -> field.getLong(0).toULong() as T
        Double::class -> field.getDouble(0.0) as T
        Float::class -> field.getFloat(0f) as T
        Integer::class -> field.getInt(0) as T
        Int::class -> field.getInt(0) as T
        UInt::class -> field.getInt(0).toUInt() as T
        JsonList::class -> field.optList() as T
        JsonMap::class -> field.optMap() as T
        JsonValue::class -> field.toJsonValue() as T
        Instant::class -> field.requireEpochMillis(key) as T
        else -> throw JsonException("Invalid type '${T::class.java.simpleName}' for field '$key'")
    }
}


/**
 * Gets the field with the given [key] and parses it as an ISO date string.
 *
 * @throws JsonException if the value is not a valid date string.
 */
@Throws(JsonException::class)
public fun JsonMap.isoDateAsInstant(key: String, defaultValue: Instant? = null): Instant? {
    return try {
        optionalField<String>(key)?.let { isoDate ->
            defaultValue?.let {
                DateUtils.parseIso8601(isoDate, it)
            } ?: run {
                DateUtils.parseIso8601(isoDate)
            }
        }
    } catch (e: Exception) {
        throw JsonException("Unable to parse value as date: ${get(key)}", e)
    }
}

/**
 * Gets the field with the given [key] as a [Duration], interpreting the stored number in
 * [unit], or `null` if not defined.
 *
 * @throws JsonException if the value is not a number.
 */
@Throws(JsonException::class)
public fun JsonMap.optionalDuration(key: String, unit: DurationUnit): Duration? {
    val field = get(key) ?: return null
    if (field.isNull) {
        return null
    }
    if (!field.isNumber) {
        throw JsonException("Unable to parse field '$key' as a duration: $field")
    }
    return field.getDouble(0.0).toDuration(unit)
}

/**
 * Gets the field with the given [key] as a [Duration], interpreting the stored number in
 * [unit].
 *
 * @throws JsonException if the field is missing, `null`, or not a number.
 */
@Throws(JsonException::class)
public fun JsonMap.requireDuration(key: String, unit: DurationUnit): Duration =
    optionalDuration(key, unit) ?: throw JsonException("Missing required field: '$key'")

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

/** Convenience method to create an empty [JsonMap]. */
public fun emptyJsonMap(): JsonMap = JsonMap.EMPTY_MAP

/** Convenience method to create an empty [JsonList]. */
public fun emptyJsonList(): JsonList = JsonList.EMPTY_LIST

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

package com.urbanairship.json

import androidx.annotation.RestrictTo
import androidx.room.TypeConverter
import com.urbanairship.UALog
import com.urbanairship.json.JsonPredicate.Companion.parse

/**
 * JSON type converters for use with Room.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class JsonTypeConverters public constructor() {

    @TypeConverter
    public fun jsonValueFromString(value: String?): JsonValue? {
        if (value == null) {
            return null
        }

        try {
            return JsonValue.parseString(value)
        } catch (e: JsonException) {
            UALog.e(e, "Unable to parse json value: $value")
            return null
        }
    }

    @TypeConverter
    public fun jsonValueToString(value: JsonValue?): String? {
        return value?.toString()
    }

    @TypeConverter
    public fun jsonMapFromString(value: String?): JsonMap? {
        if (value == null) {
            return null
        }

        try {
            return JsonValue.parseString(value).optMap()
        } catch (e: JsonException) {
            UALog.e(e, "Unable to parse json value: $value")
            return null
        }
    }

    @TypeConverter
    public fun jsonMapToString(map: JsonMap?): String? {
        return map?.toJsonValue()?.toString()
    }

    @TypeConverter
    public fun jsonPredicateToString(predicate: JsonPredicate?): String? {
        return predicate?.toJsonValue()?.toString()
    }

    @TypeConverter
    public fun jsonPredicateFromString(value: String?): JsonPredicate? {
        if (value == null) {
            return null
        }

        try {
            return parse(JsonValue.parseString(value))
        } catch (e: JsonException) {
            UALog.e(e, "Unable to parse trigger context: $value")
            return null
        }
    }
}

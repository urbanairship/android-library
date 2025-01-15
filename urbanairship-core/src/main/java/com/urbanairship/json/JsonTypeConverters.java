package com.urbanairship.json;

import com.urbanairship.UALog;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.room.TypeConverter;

/**
 * JSON type converters for use with Room.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class JsonTypeConverters {
    @Nullable
    @TypeConverter
    public JsonValue jsonValueFromString(@Nullable String value) {
        if (value == null) {
            return null;
        }

        try {
            return JsonValue.parseString(value);
        } catch (JsonException e) {
            UALog.e(e, "Unable to parse json value: " + value);
            return null;
        }
    }

    @Nullable
    @TypeConverter
    public String jsonValueToString(@Nullable JsonValue value) {
        return value == null ? null : value.toString();
    }

    @Nullable
    @TypeConverter
    public JsonMap jsonMapFromString(@Nullable String value) {
        if (value == null) {
            return null;
        }

        try {
            return JsonValue.parseString(value).optMap();
        } catch (JsonException e) {
            UALog.e(e, "Unable to parse json value: " + value);
            return null;
        }
    }

    @Nullable
    @TypeConverter
    public String jsonMapToString(@Nullable JsonMap map) {
        return map == null ? null : map.toJsonValue().toString();
    }

    @Nullable
    @TypeConverter
    public String jsonPredicateToString(@Nullable JsonPredicate predicate) {
        return predicate == null ? null : predicate.toJsonValue().toString();
    }

    @Nullable
    @TypeConverter
    public JsonPredicate jsonPredicateFromString(@Nullable String value) {
        if (value == null) {
            return null;
        }

        try {
            return JsonPredicate.parse(JsonValue.parseString(value));
        } catch (JsonException e) {
            UALog.e(e, "Unable to parse trigger context: " + value);
            return null;
        }
    }
}

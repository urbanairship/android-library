package com.urbanairship.json;

import com.urbanairship.Logger;

import androidx.annotation.RestrictTo;
import androidx.room.TypeConverter;

/**
 * JSON type converters for use with Room.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class JsonTypeConverters {
    @TypeConverter
    public JsonValue jsonValueFromString(String value) {
        if (value == null) {
            return null;
        }

        try {
            return JsonValue.parseString(value);
        } catch (JsonException e) {
            Logger.error(e, "Unable to parse json value: " + value);
            return null;
        }
    }

    @TypeConverter
    public String jsonValueToString(JsonValue value) {
        return value == null ? null : value.toString();
    }

    @TypeConverter
    public JsonMap jsonMapFromString(String value) {
        if (value == null) {
            return null;
        }

        try {
            return JsonValue.parseString(value).optMap();
        } catch (JsonException e) {
            Logger.error(e, "Unable to parse json value: " + value);
            return null;
        }
    }

    @TypeConverter
    public String jsonMapToString(JsonMap map) {
        return map == null ? null : map.toJsonValue().toString();
    }

    @TypeConverter
    public String jsonPredicateToString(JsonPredicate predicate) {
        return predicate == null ? null : predicate.toJsonValue().toString();
    }

    @TypeConverter
    public JsonPredicate jsonPredicateFromString(String value) {
        if (value == null) {
            return null;
        }

        try {
            return JsonPredicate.parse(JsonValue.parseString(value));
        } catch (JsonException e) {
            Logger.error(e, "Unable to parse trigger context: " + value);
            return null;
        }
    }
}

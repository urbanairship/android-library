/* Copyright Airship and Contributors */

package com.urbanairship.automation.storage;

import com.urbanairship.Logger;
import com.urbanairship.automation.Audience;
import com.urbanairship.automation.TriggerContext;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonPredicate;
import com.urbanairship.json.JsonValue;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.RestrictTo;
import androidx.room.TypeConverter;

/**
 * Room type converters.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class Converters {

    @TypeConverter
    public String triggerContextToString(TriggerContext context) {
        return context == null ? null : context.toJsonValue().toString();
    }

    @TypeConverter
    public TriggerContext triggerContextFromString(String value) {
        if (value == null) {
            return null;
        }

        try {
            return TriggerContext.fromJson(JsonValue.parseString(value));
        } catch (JsonException e) {
            Logger.error(e, "Unable to parse trigger context: " + value);
            return null;
        }
    }

    @TypeConverter
    public String audienceToString(Audience audience) {
        return audience == null ? null : audience.toJsonValue().toString();
    }

    @TypeConverter
    public Audience audienceFromString(String value) {
        if (value == null) {
            return null;
        }

        try {
            return Audience.fromJson(JsonValue.parseString(value));
        } catch (JsonException e) {
            Logger.error(e, "Unable to parse audience: " + value);
            return null;
        }
    }

    @TypeConverter
    public static List<String> stringArrayFromString(String value) {
        try {
            List<String> array = new ArrayList<>();
            for (JsonValue entry : JsonValue.parseString(value).optList()){
                if (entry.getString() != null) {
                    array.add(entry.optString());
                }
            }
            return array;
        } catch (JsonException e) {
            Logger.error(e, "Unable to parse string array from string: " + value);
            return null;
        }
    }

    @TypeConverter
    public static String fromArrayList(List<String> list) {
        return JsonValue.wrapOpt(list).toString();
    }
}

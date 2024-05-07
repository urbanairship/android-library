/* Copyright Airship and Contributors */

package com.urbanairship.automation.storage;

import com.urbanairship.UALog;
import com.urbanairship.audience.AudienceSelector;
import com.urbanairship.json.JsonException;
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
            UALog.e(e, "Unable to parse string array from string: " + value);
            return null;
        }
    }

    @TypeConverter
    public static String fromArrayList(List<String> list) {
        return JsonValue.wrapOpt(list).toString();
    }
}

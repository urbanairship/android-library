/* Copyright Airship and Contributors */
package com.urbanairship.automation.storage

import androidx.annotation.RestrictTo
import androidx.room.TypeConverter
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

/**
 * Room type converters.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object Converters {

    @TypeConverter
    public fun stringArrayFromString(value: String): List<String>? {
        try {
            return JsonValue.parseString(value)
                .optList()
                .mapNotNull { it.string }
        } catch (e: JsonException) {
            UALog.e(e, "Unable to parse string array from string: $value")
            return null
        }
    }

    @TypeConverter
    public fun fromArrayList(list: MutableList<String?>?): String {
        return JsonValue.wrapOpt(list).toString()
    }
}

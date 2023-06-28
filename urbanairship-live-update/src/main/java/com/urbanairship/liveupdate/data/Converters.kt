/* Copyright Airship and Contributors */

package com.urbanairship.liveupdate.data

import androidx.room.TypeConverter
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

/**
 * @hide
 */
internal class Converters {
    @TypeConverter
    fun fromJsonMap(value: JsonMap): String = value.toString()

    @TypeConverter
    fun toJsonMap(value: String): JsonMap {
        return JsonValue.parseString(value).requireMap()
    }
}

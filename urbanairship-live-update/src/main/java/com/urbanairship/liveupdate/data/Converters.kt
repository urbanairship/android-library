/* Copyright Airship and Contributors */

package com.urbanairship.liveupdate.data

import androidx.room.TypeConverter
import com.urbanairship.UALog
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
        return try {
            JsonValue.parseString(value).optMap()
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to parse stored JsonMap, returning empty map" }
            JsonMap.EMPTY_MAP
        }
    }
}

package com.urbanairship.cache

import androidx.annotation.RestrictTo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.urbanairship.json.JsonTypeConverters
import com.urbanairship.json.JsonValue

/**
 * @hide
 */
@Entity(tableName = "cacheItems")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@TypeConverters(JsonTypeConverters::class)
public data class CacheEntity(
    @PrimaryKey val key: String,
    val appVersion: String,
    val sdkVersion: String,
    val expireOn: Long,
    val data: JsonValue
) {
    public fun isExpired(timestamp: Long): Boolean = timestamp > expireOn
}

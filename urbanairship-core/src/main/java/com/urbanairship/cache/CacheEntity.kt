package com.urbanairship.cache

import androidx.annotation.RestrictTo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.urbanairship.json.JsonTypeConverters
import com.urbanairship.json.JsonValue
import com.urbanairship.util.TimeTypeConverters
import java.time.Instant

/**
 * @hide
 */
@Entity(tableName = "cacheItems")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@TypeConverters(JsonTypeConverters::class, TimeTypeConverters::class)
public data class CacheEntity(
    @PrimaryKey val key: String,
    val appVersion: String,
    val sdkVersion: String,
    val expireOn: Instant,
    val data: JsonValue
) {
    public fun isExpired(timestamp: Instant): Boolean = timestamp > expireOn
}

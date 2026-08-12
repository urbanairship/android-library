/* Copyright Airship and Contributors */

package com.urbanairship.util

import androidx.annotation.RestrictTo
import androidx.room.TypeConverter
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Room converters for [Instant] and [Duration].
 *
 * Both are stored as epoch/whole milliseconds, matching the `Long` columns these types
 * replaced, so no schema migration is required.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class TimeTypeConverters {

    @TypeConverter
    public fun instantFromMillis(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    public fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    public fun durationFromMillis(value: Long?): Duration? = value?.milliseconds

    @TypeConverter
    public fun durationToMillis(value: Duration?): Long? = value?.inWholeMilliseconds
}

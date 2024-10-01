/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Entity(tableName = "constraints", indices = [Index(value = ["constraintId"], unique = true)])
internal class ConstraintEntity {
    @PrimaryKey(autoGenerate = true)
    var id = 0
    var constraintId: String? = null
    var count = 0

    // Kotlin uses a Value Class for Duration, so we can't use a Room type converter

    /** Prefer using [range]. Raw number of seconds in the constraint's time range. */
    @ColumnInfo(name = "range")
    var _rawRange: Long = 0

    /** Number of seconds in the constraint's time range. */
    var range: Duration
        get() = _rawRange.seconds
        set(value) {
            _rawRange = value.inWholeSeconds
        }
}

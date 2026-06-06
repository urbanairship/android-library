/* Copyright Airship and Contributors */
package com.urbanairship.preferences

import androidx.annotation.RestrictTo
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** @hide
 */
@Entity(tableName = "preferences")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PreferenceData public constructor(
    @field:ColumnInfo(name = "_id")
    @field:PrimaryKey
    public var key: String,

    @field:ColumnInfo(name = "value")
    public var value: String?,

    /**
     * `true` when the value is intended for lazy access (AsyncPrefKey) and should be skipped
     * during the eager takeoff load. Defaults to `false` so existing rows preserve today's
     * behavior after the schema migration.
     */
    @field:ColumnInfo(name = "lazy", defaultValue = "0")
    public var lazy: Boolean = false
)

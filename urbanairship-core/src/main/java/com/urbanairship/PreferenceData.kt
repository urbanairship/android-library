/* Copyright Airship and Contributors */
package com.urbanairship

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
    public var value: String?
)

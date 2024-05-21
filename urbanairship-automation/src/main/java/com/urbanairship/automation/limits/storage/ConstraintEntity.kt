/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits.storage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "constraints", indices = [Index(value = ["constraintId"], unique = true)])
internal class ConstraintEntity {
    @PrimaryKey(autoGenerate = true)
    var id = 0
    var constraintId: String? = null
    var count = 0
    var range: Long = 0
}

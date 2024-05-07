/* Copyright Airship and Contributors */
package com.urbanairship.automation.limits.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "occurrences",
    foreignKeys = [ForeignKey(
        onDelete = CASCADE,
        entity = ConstraintEntity::class,
        parentColumns = ["constraintId"],
        childColumns = ["parentConstraintId"]
    )],
    indices = [Index("parentConstraintId")]
)
internal class OccurrenceEntity {

    @PrimaryKey(autoGenerate = true)
    var id = 0
    var parentConstraintId: String? = null
    var timeStamp: Long = 0

    class Comparator : kotlin.Comparator<OccurrenceEntity> {
        override fun compare(self: OccurrenceEntity, other: OccurrenceEntity): Int {
            return java.lang.Long.compare(self.timeStamp, other.timeStamp)
        }
    }
}

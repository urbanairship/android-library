/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits.storage;

import androidx.annotation.RestrictTo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Entity(tableName = "occurrences", foreignKeys = {
        @ForeignKey(onDelete = ForeignKey.CASCADE, entity = ConstraintEntity.class,
                parentColumns = "constraintId", childColumns = "parentConstraintId") },
        indices = { @Index("parentConstraintId") })

public class OccurrenceEntity {
    @PrimaryKey(autoGenerate = true)
    int id;

    public String parentConstraintId;
    public long timeStamp;
}

/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits.storage;

import androidx.annotation.RestrictTo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Entity(tableName = "constraints", indices = { @Index(value = { "constraintId" }, unique = true) })
public class ConstraintEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String constraintId;
    public int count;
    public long range;
}

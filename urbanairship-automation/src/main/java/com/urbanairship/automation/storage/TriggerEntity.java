/* Copyright Airship and Contributors */

package com.urbanairship.automation.storage;

import com.urbanairship.json.JsonPredicate;

import androidx.annotation.RestrictTo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Entity(tableName = "triggers", foreignKeys = {
        @ForeignKey(onDelete = ForeignKey.CASCADE, entity = ScheduleEntity.class,
                parentColumns = "scheduleId", childColumns = "parentScheduleId") },
        indices = { @Index("parentScheduleId") })
//TODO: migrate to kotlin on the next version update
public class TriggerEntity {

    @PrimaryKey(autoGenerate = true)
    int id;

    public int triggerType;
    public double goal;
    public JsonPredicate jsonPredicate;
    public boolean isCancellation;
    public double progress;
    public String parentScheduleId;

    @Ignore
    @Override
    public String toString() {
        return "TriggerEntity{" +
                "id=" + id +
                ", triggerType=" + triggerType +
                ", goal=" + goal +
                ", jsonPredicate=" + jsonPredicate +
                ", isCancellation=" + isCancellation +
                ", progress=" + progress +
                ", parentScheduleId='" + parentScheduleId + '\'' +
                '}';
    }

}

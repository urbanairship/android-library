/* Copyright Airship and Contributors */

package com.urbanairship.automation.storage;

import com.urbanairship.audience.AudienceSelector;
import com.urbanairship.automation.TriggerContext;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.util.List;

import androidx.annotation.RestrictTo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Entity(tableName = "schedules", indices = { @Index(value = { "scheduleId" }, unique = true) })
public class ScheduleEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String scheduleId;
    public String group;
    public JsonMap metadata;
    public int limit;
    public int priority;
    public long triggeredTime;
    public long scheduleStart;
    public long scheduleEnd;
    public long editGracePeriod;
    public long interval;
    public String scheduleType;
    public JsonValue data;
    public int count;
    public int executionState;
    public long executionStateChangeDate;
    public TriggerContext triggerContext;
    public int appState;
    public List<String> screens;
    public long seconds;
    public String regionId;
    public AudienceSelector audience;
    public JsonValue campaigns;
    public JsonValue reportingContext;

    public List<String> frequencyConstraintIds;

    public String messageType;
    public boolean bypassHoldoutGroups;
    public long newUserEvaluationDate;

    @Override
    public String toString() {
        return "ScheduleEntity{" +
                "id=" + id +
                ", scheduleId='" + scheduleId + '\'' +
                ", group='" + group + '\'' +
                ", metadata=" + metadata +
                ", limit=" + limit +
                ", priority=" + priority +
                ", triggeredTime=" + triggeredTime +
                ", scheduleStart=" + scheduleStart +
                ", scheduleEnd=" + scheduleEnd +
                ", editGracePeriod=" + editGracePeriod +
                ", interval=" + interval +
                ", scheduleType='" + scheduleType + '\'' +
                ", data=" + data +
                ", count=" + count +
                ", executionState=" + executionState +
                ", executionStateChangeDate=" + executionStateChangeDate +
                ", triggerContext=" + triggerContext +
                ", appState=" + appState +
                ", screens=" + screens +
                ", seconds=" + seconds +
                ", regionId='" + regionId + '\'' +
                ", audience=" + audience +
                ", campaigns=" + campaigns +
                ", reportingContext=" + reportingContext +
                ", frequencyConstraintIds=" + frequencyConstraintIds +
                ", messageType=" + messageType +
                ", bypassHoldoutGroups=" + bypassHoldoutGroups +
                ", newUserEvaluationDate=" + newUserEvaluationDate +
                '}';
    }

}

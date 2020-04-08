/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Class encapsulating the implementor-set information for an action schedule.
 */
public class ActionScheduleInfo implements ScheduleInfo, Parcelable {

    /**
     * @hide
     */
    @NonNull
    public static final Creator<ActionScheduleInfo> CREATOR = new Creator<ActionScheduleInfo>() {

        @NonNull
        @Override
        public ActionScheduleInfo createFromParcel(@NonNull Parcel in) {
            return new ActionScheduleInfo(in);
        }

        @NonNull
        @Override
        public ActionScheduleInfo[] newArray(int size) {
            return new ActionScheduleInfo[size];
        }
    };

    /**
     * The triggers limit for a single schedule.
     */
    public static final long TRIGGER_LIMIT = 10;

    /**
     * Actions json key.
     */
    @NonNull
    public static final String ACTIONS_KEY = "actions";

    private final List<Trigger> triggers;
    private final Map<String, JsonValue> actions;
    private final int limit;
    private final int priority;
    private final String group;
    private final long start;
    private final long end;
    private final ScheduleDelay delay;
    private final long editGracePeriod;
    private final long interval;

    private ActionScheduleInfo(@NonNull Builder builder) {
        this.triggers = builder.triggers;
        this.actions = builder.actions;
        this.limit = builder.limit;
        this.priority = builder.priority;
        this.group = builder.group;
        this.start = builder.start;
        this.end = builder.end;
        this.delay = builder.delay;
        this.editGracePeriod = builder.editGracePeriod;
        this.interval = builder.interval;
    }

    protected ActionScheduleInfo(@NonNull Parcel in) {
        this.triggers = in.createTypedArrayList(Trigger.CREATOR);
        this.limit = in.readInt();
        this.priority = in.readInt();
        this.group = in.readString();
        this.start = in.readLong();
        this.end = in.readLong();
        this.editGracePeriod = in.readLong();
        this.interval = in.readLong();

        this.actions = JsonValue.wrapOpt(in.readParcelable(JsonValue.class.getClassLoader()))
                                .optMap()
                                .getMap();

        this.delay = in.readParcelable(ScheduleDelay.class.getClassLoader());
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeTypedList(triggers);
        dest.writeInt(limit);
        dest.writeInt(priority);
        dest.writeString(group);
        dest.writeLong(start);
        dest.writeLong(end);
        dest.writeLong(editGracePeriod);
        dest.writeLong(interval);
        dest.writeParcelable(JsonValue.wrapOpt(actions), flags);
        dest.writeParcelable(delay, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Creates a new Builder instance.
     *
     * @return The Builder instance.
     */
    @NonNull
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<Trigger> getTriggers() {
        return triggers;
    }

    @NonNull
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public JsonValue getData() {
        return JsonValue.wrapOpt(actions);
    }

    /**
     * Gets the scheduled actions.
     *
     * @return A map of action names to action values.
     */
    @NonNull
    public Map<String, JsonValue> getActions() {
        return actions;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLimit() {
        return limit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getPriority() {
        return priority;
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public String getGroup() {
        return group;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getStart() {
        return start;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getEnd() {
        return end;
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public ScheduleDelay getDelay() {
        return delay;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getEditGracePeriod() {
        return editGracePeriod;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getInterval() {
        return interval;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ActionScheduleInfo info = (ActionScheduleInfo) o;

        if (limit != info.limit) return false;
        if (priority != info.priority) return false;
        if (start != info.start) return false;
        if (end != info.end) return false;
        if (editGracePeriod != info.editGracePeriod) return false;
        if (interval != info.interval) return false;
        if (!triggers.equals(info.triggers)) return false;
        if (!actions.equals(info.actions)) return false;
        if (group != null ? !group.equals(info.group) : info.group != null) return false;
        return delay != null ? delay.equals(info.delay) : info.delay == null;
    }

    @Override
    public int hashCode() {
        int result = triggers.hashCode();
        result = 31 * result + actions.hashCode();
        result = 31 * result + limit;
        result = 31 * result + priority;
        result = 31 * result + (group != null ? group.hashCode() : 0);
        result = 31 * result + (int) (start ^ (start >>> 32));
        result = 31 * result + (int) (end ^ (end >>> 32));
        result = 31 * result + (delay != null ? delay.hashCode() : 0);
        result = 31 * result + (int) (editGracePeriod ^ (editGracePeriod >>> 32));
        result = 31 * result + (int) (interval ^ (interval >>> 32));
        return result;
    }

    /**
     * {@see #fromJson(JsonValue)}
     *
     * @param value The json value.
     * @return The parsed ActionScheduleInfo.
     * @throws JsonException If the JSON is invalid.
     * @deprecated To be removed in SDK 12. Use {@link #fromJson(JsonValue)} instead.
     */
    @Deprecated
    @NonNull
    public static ActionScheduleInfo parseJson(@NonNull JsonValue value) throws JsonException {
        return fromJson(value);
    }

    /**
     * Parses an ActionScheduleInfo from a JsonValue.
     * <p>
     * The expected JsonValue is a map containing:
     * <pre>
     * - "group": Optional. Group identifier. Useful to cancel schedules for a specific campaign.
     * - "start": Optional. Start time as an ISO 8601 timestamp. Time before the schedule starts listening for events.
     * - "end": Optional. End time as an ISO 8601 timestamp. After the schedule is past the end time it will automatically be canceled.
     * - "triggers": Required. An array of triggers.
     * - "limit": Optional, defaults to 1. Number of times to trigger the actions payload before cancelling the schedule.
     * - "priority": Optional, defaults to 0. In case of conflict, schedules will be executed by priority in ascending order.
     * - "actions": Required. Actions payload to run when one or more of the triggers meets its goal.
     * - "delay": Optional {@link ScheduleDelay}.
     * </pre>
     *
     * @param value The schedule.
     * @return The parsed ActionScheduleInfo.
     * @throws JsonException If the JsonValue does not produce a valid ActionScheduleInfo.
     */
    @NonNull
    public static ActionScheduleInfo fromJson(@NonNull JsonValue value) throws JsonException {
        JsonMap jsonMap = value.optMap();

        ActionScheduleInfo.Builder builder = newBuilder()
                .addAllActions(jsonMap.opt(ACTIONS_KEY).optMap())
                .setLimit(jsonMap.opt(LIMIT_KEY).getInt(1))
                .setPriority(jsonMap.opt(PRIORITY_KEY).getInt(0))
                .setGroup(jsonMap.opt(GROUP_KEY).getString());

        if (jsonMap.containsKey(END_KEY)) {
            builder.setEnd(DateUtils.parseIso8601(jsonMap.opt(END_KEY).optString(), -1));
        }

        if (jsonMap.containsKey(START_KEY)) {
            builder.setStart(DateUtils.parseIso8601(jsonMap.opt(START_KEY).optString(), -1));
        }

        for (JsonValue triggerJson : jsonMap.opt(TRIGGERS_KEY).optList()) {
            builder.addTrigger(Trigger.fromJson(triggerJson));
        }

        if (jsonMap.containsKey(DELAY_KEY)) {
            builder.setDelay(ScheduleDelay.fromJson(jsonMap.opt(DELAY_KEY)));
        }

        if (jsonMap.containsKey(EDIT_GRACE_PERIOD)) {
            builder.setEditGracePeriod(jsonMap.opt(EDIT_GRACE_PERIOD).getLong(0), TimeUnit.DAYS);
        }

        if (jsonMap.containsKey(INTERVAL)) {
            builder.setInterval(jsonMap.opt(INTERVAL).getLong(0), TimeUnit.SECONDS);
        }

        try {
            return builder.build();
        } catch (IllegalArgumentException e) {
            throw new JsonException("Invalid schedule info", e);
        }
    }

    /**
     * Builder class.
     */
    public static class Builder {

        private final List<Trigger> triggers = new ArrayList<>();
        private final Map<String, JsonValue> actions = new HashMap<>();
        private String group;
        private long start = -1;
        private long end = -1;
        private int limit = 1;
        private int priority = 0;
        private ScheduleDelay delay = null;
        private long editGracePeriod = -1;
        private long interval;

        /**
         * Adds a trigger.
         *
         * @param trigger A trigger instance.
         * @return The Builder instance.
         */
        @NonNull
        public Builder addTrigger(@NonNull Trigger trigger) {
            this.triggers.add(trigger);
            return this;
        }

        /**
         * Adds a list of triggers.
         *
         * @param triggers A list of trigger instances.
         * @return The Builder instance.
         */
        @NonNull
        public Builder addTriggers(@NonNull List<Trigger> triggers) {
            this.triggers.addAll(triggers);
            return this;
        }

        /**
         * Adds an action.
         *
         * @param actionName The action name.
         * @param actionValue The action value.
         * @return The Builder instance.
         */
        @NonNull
        public Builder addAction(@NonNull String actionName, @NonNull JsonSerializable actionValue) {
            actions.put(actionName, actionValue.toJsonValue());
            return this;
        }

        /**
         * Adds a map of actions.
         *
         * @param actionMap A map of action names to action values.
         * @return The Builder instance.
         */
        @NonNull
        public Builder addAllActions(@NonNull JsonMap actionMap) {
            actions.putAll(actionMap.getMap());
            return this;
        }

        /**
         * Sets the fulfillment limit.
         *
         * @param limit The limit.
         * @return The Builder instance.
         */
        @NonNull
        public Builder setLimit(int limit) {
            this.limit = limit;
            return this;
        }

        /**
         * Sets the priority level, in ascending order.
         *
         * @param priority The priority level.
         * @return The Builder instance.
         */
        @NonNull
        public Builder setPriority(int priority) {
            this.priority = priority;
            return this;
        }

        /**
         * Sets the group.
         *
         * @param group The group.
         * @return The Builder instance.
         */
        @NonNull
        public Builder setGroup(@Nullable String group) {
            this.group = group;
            return this;
        }

        /**
         * Sets the start time in ms.
         *
         * @param start The start time in ms.
         * @return The Builder instance.
         */
        @NonNull
        public Builder setStart(long start) {
            this.start = start;
            return this;
        }

        /**
         * Sets the end time in ms.
         *
         * @param end The end time in ms.
         * @return The Builder instance.
         */
        @NonNull
        public Builder setEnd(long end) {
            this.end = end;
            return this;
        }

        /**
         * Set a schedule delay.
         *
         * @param delay A ScheduleDelay object.
         * @return The Builder instance.
         */
        @NonNull
        public Builder setDelay(@Nullable ScheduleDelay delay) {
            this.delay = delay;
            return this;
        }

        /**
         * Sets the edit grace period after a schedule expires or finishes.
         *
         * @param duration The grace period.
         * @param timeUnit The time unit.
         * @return The Builder instance.
         */
        @NonNull
        public Builder setEditGracePeriod(@IntRange(from = 0) long duration, @NonNull TimeUnit timeUnit) {
            this.editGracePeriod = timeUnit.toMillis(duration);
            return this;
        }

        /**
         * Sets the execution interval.
         *
         * @param duration The interval.
         * @param timeUnit The time unit.
         * @return The Builder instance.
         */
        @NonNull
        public Builder setInterval(@IntRange(from = 0) long duration, @NonNull TimeUnit timeUnit) {
            this.interval = timeUnit.toMillis(duration);
            return this;
        }

        /**
         * Builds the ActionScheduleInfo instance.
         *
         * @return The new ActionScheduleInfo instance.
         * @throws IllegalArgumentException if either no actions are set, no triggers or more than
         * {@link #TRIGGER_LIMIT} triggers are set, or the start time is set after the end time.
         */
        @NonNull
        public ActionScheduleInfo build() {
            if (actions.isEmpty()) {
                throw new IllegalArgumentException("Actions required.");
            }

            if (start > -1 && end > -1 && end < start) {
                throw new IllegalArgumentException("End must be after start.");
            }

            if (triggers.isEmpty()) {
                throw new IllegalArgumentException("Must contain at least 1 trigger.");
            }

            if (triggers.size() > TRIGGER_LIMIT) {
                throw new IllegalArgumentException("No more than " + TRIGGER_LIMIT + " triggers allowed.");
            }

            return new ActionScheduleInfo(this);
        }

    }

}

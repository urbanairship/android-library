/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class encapsulating the implementor-set information for an action schedule.
 */
public class ActionScheduleInfo implements Parcelable {

    public static final Creator<ActionScheduleInfo> CREATOR = new Creator<ActionScheduleInfo>() {
        @Override
        public ActionScheduleInfo createFromParcel(Parcel in) {
            return new ActionScheduleInfo(in);
        }

        @Override
        public ActionScheduleInfo[] newArray(int size) {
            return new ActionScheduleInfo[size];
        }
    };

    /**
     * The triggers limit for a single schedule.
     */
    public static final long TRIGGER_LIMIT = 10;

    private final List<Trigger> triggers;
    private final Map<String, JsonValue> actions;
    private final int limit;
    private final String group;
    private final long start;
    private final long end;
    private final ScheduleDelay delay;

    private ActionScheduleInfo(Builder builder) {
        this.triggers = builder.triggers;
        this.actions = builder.actions;
        this.limit = builder.limit;
        this.group = builder.group;
        this.start = builder.start;
        this.end = builder.end;
        this.delay = builder.delay;
    }

    protected ActionScheduleInfo(Parcel in) {
        this.triggers = in.createTypedArrayList(Trigger.CREATOR);
        this.limit = in.readInt();
        this.group = in.readString();
        this.start = in.readLong();
        this.end = in.readLong();

        this.actions = JsonValue.wrapOpt(in.readParcelable(JsonValue.class.getClassLoader()))
                                .optMap()
                                .getMap();

        this.delay = in.readParcelable(ScheduleDelay.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(triggers);
        dest.writeInt(limit);
        dest.writeString(group);
        dest.writeLong(start);
        dest.writeLong(end);
        dest.writeParcelable(JsonValue.wrapOpt(actions), flags);
        dest.writeParcelable(JsonValue.wrapOpt(delay), flags);
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
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Gets the action triggers.
     *
     * @return A list of triggers.
     */
    public List<Trigger> getTriggers() {
        return triggers;
    }

    /**
     * Gets the scheduled actions.
     *
     * @return A map of action names to action values.
     */
    public Map<String, JsonValue> getActions() {
        return actions;
    }

    /**
     * Gets the schedule fulfillment limit.
     *
     * @return The fulfillment limit.
     */
    public int getLimit() {
        return limit;
    }

    /**
     * Gets the schedule group.
     *
     * @return The schedule group.
     */
    public String getGroup() {
        return group;
    }

    /**
     * Gets the schedule start time in MS.
     *
     * @return The schedule start time in MS.
     */
    public long getStart() {
        return start;
    }

    /**
     * Gets the schedule end time in MS.
     *
     * @return The schedule end time in MS.
     */
    public long getEnd() {
        return end;
    }

    /**
     * Gets the schedule's delay.
     *
     * @return A ScheduleDelay instance.
     */
    public ScheduleDelay getDelay() {
        return delay;
    }

    /**
     * Parses an ActionScheduleInfo from a JsonValue.
     * </p>
     * The expected JsonValue is a map containing:
     * <pre>
     * - "group": Optional. Group identifier. Useful to cancel schedules for a specific campaign.
     * - "start": Optional. Start time as an ISO 8601 timestamp. Time before the schedule starts listening for events.
     * - "end": Optional. End time as an ISO 8601 timestamp. After the schedule is past the end time it will automatically be canceled.
     * - "triggers": Required. An array of triggers. Trigger payload as defined by {@link Trigger#predicate}.
     * - "limit": Optional, defaults to 1. Number of times to trigger the actions payload before cancelling the schedule.
     * - "actions": Required. Actions payload to run when one or more of the triggers meets its goal.
     *</pre>
     *
     * @param value The schedule.
     * @return The parsed ActionScheduleInfo.
     * @throws JsonException If the JsonValue does not produce a valid ActionScheduleInfo.
     */
    public static ActionScheduleInfo parseJson(@NonNull JsonValue value) throws JsonException {
        JsonMap jsonMap = value.optMap();

        ActionScheduleInfo.Builder builder = newBuilder()
                .addAllActions(jsonMap.opt("actions").optMap())
                .setLimit(jsonMap.opt("limit").getInt(1))
                .setGroup(jsonMap.opt("group").getString(null));

        if (jsonMap.containsKey("end")) {
            builder.setEnd(DateUtils.parseIso8601(jsonMap.opt("end").getString(), -1));
        }

        if (jsonMap.containsKey("start")) {
            builder.setStart(DateUtils.parseIso8601(jsonMap.opt("start").getString(), -1));
        }

        for (JsonValue triggerJson : jsonMap.opt("triggers").optList()) {
            builder.addTrigger(Trigger.parseJson(triggerJson));
        }

        if (jsonMap.containsKey("delay")) {
            builder.setDelay(ScheduleDelay.parseJson(jsonMap.opt("delay")));
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
        private List<Trigger> triggers = new ArrayList<>();
        private Map<String, JsonValue> actions = new HashMap<>();
        private String group;
        private long start = -1;
        private long end = -1;
        private int limit = 1;
        private ScheduleDelay delay = null;

        /**
         * Adds a trigger.
         *
         * @param trigger A trigger instance.
         * @return The Builder instance.
         */
        public Builder addTrigger(Trigger trigger) {
            this.triggers.add(trigger);
            return this;
        }

        /**
         * Adds an action.
         *
         * @param actionName The action name.
         * @param actionValue The action value.
         * @return The Builder instance.
         */
        public Builder addAction(String actionName, JsonSerializable actionValue) {
            actions.put(actionName, actionValue.toJsonValue());
            return this;
        }

        /**
         * Adds a map of actions.
         *
         * @param actionMap A map of action names to action values.
         * @return The Builder instance.
         */
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
        public Builder setLimit(int limit) {
            this.limit = limit;
            return this;
        }

        /**
         * Sets the group.
         *
         * @param group The group.
         * @return The Builder instance.
         */
        public Builder setGroup(String group) {
            this.group = group;
            return this;
        }

        /**
         * Sets the start time in MS.
         *
         * @param start The start time in MS.
         * @return The Builder instance.
         */
        public Builder setStart(long start) {
            this.start = start;
            return this;
        }

        /**
         * Sets the end time in MS.
         *
         * @param end The end time in MS.
         * @return The Builder instance.
         */
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
        public Builder setDelay(ScheduleDelay delay) {
            this.delay = delay;
            return this;
        }

        /**
         * Builds the ActionScheduleInfo instance.
         *
         * @return The new ActionScheduleInfo instance.
         * @throws IllegalArgumentException if either no actions are set, no triggers or more than
         * {@link #TRIGGER_LIMIT} triggers are set, or the start time is set after the end time.
         */
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

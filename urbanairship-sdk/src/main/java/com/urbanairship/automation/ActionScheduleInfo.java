/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.support.annotation.NonNull;

import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class encapsulating the implementor-set information for an action schedule.
 */
public class ActionScheduleInfo {

    private final List<Trigger> triggers;
    private final Map<String, JsonSerializable> actions;
    private final int limit;
    private final String group;
    private final long start;
    private final long end;

    private ActionScheduleInfo(Builder builder) {
        this.triggers = builder.triggers;
        this.actions = builder.actions;
        this.limit = builder.limit;
        this.group = builder.group;
        this.start = builder.start;
        this.end = builder.end;
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
    public Map<String, JsonSerializable> getActions() {
        return actions;
    }

    /**
     * Gets the schedule actions as a list of {@link ActionRunRequest} objects.
     *
     * @return A list of {@link ActionRunRequest} objects.
     */
    public List<ActionRunRequest> getActionRunRequests() {
        List<ActionRunRequest> list = new ArrayList<>();
        for (Map.Entry<String, JsonSerializable> entry : actions.entrySet()) {
            ActionRunRequest actionRunRequest = ActionRunRequest.createRequest(entry.getKey()).setValue(entry.getValue());
            list.add(actionRunRequest);
        }

        return list;
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
     * Builder class.
     */
    public static class Builder {
        private List<Trigger> triggers = new ArrayList<>();
        private Map<String, JsonSerializable> actions = new HashMap<>();
        private String group;
        private long start = -1;
        private long end = -1;
        private int limit = 1;

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
            actions.put(actionName, actionValue);
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
         * Builds the ActionScheduleInfo instance.
         *
         * @return The new ActionScheduleInfo instance.
         * @throws IllegalStateException if either no actions are set
         * or the start time is set after the end time.
         */
        public ActionScheduleInfo build() {
            if (actions.isEmpty()) {
                throw new IllegalStateException("Actions required.");
            }

            if (start > -1 && end > -1 && end < start) {
                throw new IllegalStateException("End must be after start.");
            }

            return new ActionScheduleInfo(this);
        }
    }
}

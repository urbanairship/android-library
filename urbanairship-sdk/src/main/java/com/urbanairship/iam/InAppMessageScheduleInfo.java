/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.automation.ScheduleDelay;
import com.urbanairship.automation.ScheduleInfo;
import com.urbanairship.automation.Trigger;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.util.Checks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Class encapsulating the implementor-set information for an in-app message schedule.
 */
public class InAppMessageScheduleInfo implements ScheduleInfo {

    /**
     * The triggers limit for a single schedule.
     */
    public static final long TRIGGER_LIMIT = 10;

    private final int limit;
    private final long start;
    private final long end;
    private final List<Trigger> triggers;
    private final ScheduleDelay delay;
    private final InAppMessage message;
    private final int priority;

    /**
     * Default constructor.
     *
     * @param builder The schedule builder.
     */
    private InAppMessageScheduleInfo(Builder builder) {
        this.limit = builder.limit;
        this.start = builder.start;
        this.end = builder.end;
        this.triggers = Collections.unmodifiableList(builder.triggers);
        this.delay = builder.delay;
        this.message = builder.message;
        this.priority = builder.priority;
    }

    /**
     * Gets the schedule's in-app message.
     *
     * @return The schedule's in-app message.
     */
    public InAppMessage getInAppMessage() {
        return message;
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
    @Override
    public List<Trigger> getTriggers() {
        return triggers;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JsonSerializable getData() {
        return message;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getGroup() {
        return message.getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScheduleDelay getDelay() {
        return delay;
    }

    /**
     * Create a new builder.
     *
     * @return A new builder instance.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * In-app message schedule info builder.
     */
    public static class Builder {

        public int limit = 1;
        public long start = -1;
        public long end = -1;
        public final List<Trigger> triggers = new ArrayList<>();
        public ScheduleDelay delay;
        public InAppMessage message;
        public int priority;

        private Builder() {}

        /**
         * Sets the display limit.
         *
         * @param limit The display limit.
         * @return The builder instance.
         */
        public Builder setLimit(int limit) {
            this.limit = limit;
            return this;
        }

        /**
         * Sets the in-app message.
         *
         * @param message The in-app message.
         * @return The builder instance.
         */
        public Builder setMessage(InAppMessage message) {
            this.message = message;
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
         * Sets the priority level, in ascending order.
         *
         * @param priority The priority level.
         * @return The Builder instance.
         */
        public Builder setPriority(int priority) {
            this.priority = priority;
            return this;
        }

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
         * Adds a list of triggers.
         *
         * @param triggers A list of trigger instances.
         * @return The Builder instance.
         */
        public Builder addTriggers(List<Trigger> triggers) {
            this.triggers.addAll(triggers);
            return this;
        }

        /**
         * Builds the in-app message schedule.
         *
         * @return The new in-app message schedule.
         * @throws IllegalArgumentException if the no triggers are set, missing a valid in-app message,
         * {@link #TRIGGER_LIMIT} triggers are set, or the start time is set after the end time.
         */
        public InAppMessageScheduleInfo build() {
            Checks.checkNotNull(message, "Missing message.");
            Checks.checkArgument(((start == -1 && end == -1) || start < end), "End must be after start.");
            Checks.checkArgument(triggers.size() > 0, "Must contain at least 1 trigger.");
            Checks.checkArgument(triggers.size() <= TRIGGER_LIMIT, "No more than " + TRIGGER_LIMIT + " triggers allowed.");
            return new InAppMessageScheduleInfo(this);
        }
    }
}

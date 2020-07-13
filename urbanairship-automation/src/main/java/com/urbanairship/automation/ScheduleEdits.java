/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import com.urbanairship.iam.InAppMessage;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;

import java.util.concurrent.TimeUnit;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Schedule edits.
 */
public class ScheduleEdits {

    private final Integer limit;
    private final Long start;
    private final Long end;
    private final JsonSerializable data;
    private final Integer priority;
    private final Long editGracePeriod;
    private final Long interval;
    private final JsonMap metadata;
    @Schedule.Type
    private final String type;

    private ScheduleEdits(@NonNull Builder builder) {
        this.limit = builder.limit;
        this.start = builder.start;
        this.end = builder.end;
        this.data = builder.data;
        this.type = builder.type;
        this.priority = builder.priority;
        this.interval = builder.interval;
        this.editGracePeriod = builder.editGracePeriod;
        this.metadata = builder.metadata;
    }

    /**
     * Gets the schedule data.
     *
     * @return Schedule data.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Nullable
    public JsonSerializable getData() {
        return data;
    }

    /**
     * Gets the schedule fulfillment limit.
     *
     * @return The fulfillment limit.
     */
    @Nullable
    public Integer getLimit() {
        return limit;
    }

    /**
     * Gets the schedule priority level.
     *
     * @return The priority level.
     */
    @Nullable
    public Integer getPriority() {
        return priority;
    }

    /**
     * Gets the schedule start time in ms.
     *
     * @return The schedule start time in ms.
     */
    @Nullable
    public Long getStart() {
        return start;
    }

    /**
     * Gets the schedule end time in ms.
     *
     * @return The schedule end time in ms.
     */
    @Nullable
    public Long getEnd() {
        return end;
    }

    /**
     * Gets the schedule interval in ms.
     *
     * @return The schedule interval in ms.
     */
    @Nullable
    public Long getInterval() {
        return interval;
    }

    /**
     * Gets the schedule edit grace period in ms.
     *
     * @return The schedule edit grace period in ms.
     */
    @Nullable
    public Long getEditGracePeriod() {
        return editGracePeriod;
    }

    /**
     * Gets the schedule's metadata.
     *
     * @return The schedule's metadata.
     */
    @Nullable
    public JsonMap getMetadata() {
        return metadata;
    }

    /**
     * Gets the schedule's type.
     *
     * @return The schedule's type.
     */
    @Schedule.Type
    @Nullable
    public String getType() {
        return type;
    }

    /**
     * Create a new builder.
     *
     * @return A new builder instance.
     */
    @NonNull
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Create a new builder that extends an edits instance.
     *
     * @param edits Edits to extend.
     * @return A new builder instance.
     */
    @NonNull
    public static Builder newBuilder(@NonNull ScheduleEdits edits) {
        return new Builder(edits);
    }

    /**
     * {@link ScheduleEdits} builder.
     */
    public static class Builder {

        private Integer limit;
        private Long start;
        private Long end;
        private Integer priority;
        private Long editGracePeriod;
        private Long interval;
        private JsonMap metadata;
        private JsonSerializable data;

        @Schedule.Type
        private String type;

        private Builder() {
        }

        private Builder(@NonNull ScheduleEdits edits) {
            this.limit = edits.limit;
            this.start = edits.start;
            this.end = edits.end;
            this.data = edits.data;
            this.priority = edits.priority;
            this.type = edits.type;
        }

        /**
         * Sets the display limit.
         *
         * @param limit The display limit.
         * @return The builder instance.
         */
        @NonNull
        public Builder setLimit(int limit) {
            this.limit = limit;
            return this;
        }

        /**
         * Sets the schedule data as an in-app message.
         *
         * @param message The in-app message.
         * @return The builder instance.
         */
        @NonNull
        public Builder setData(@NonNull InAppMessage message) {
            this.data = message;
            this.type = Schedule.TYPE_IN_APP_MESSAGE;
            return this;
        }

        /**
         * Sets the schedule data as actions.
         *
         * @param actions The actions.
         * @return The builder instance.
         */
        @NonNull
        public Builder setData(@NonNull JsonMap actions) {
            this.data = actions;
            this.type = Schedule.TYPE_ACTION;
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
         * Sets the display interval.
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
         * Sets the metadata.
         *
         * @param metadata The metadata.
         * @return The Builder instance.
         */
        @NonNull
        public Builder setMetadata(@Nullable JsonMap metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Builds the in-app message schedule edits.
         *
         * @return The schedule edits.
         */
        @NonNull
        public ScheduleEdits build() {
            return new ScheduleEdits(this);
        }

    }

}

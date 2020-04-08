/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Edits for an action schedule.
 */
public class ActionScheduleEdits implements ScheduleEdits {

    private final Integer limit;
    private final Long start;
    private final Long end;
    private final Map<String, JsonValue> actions;
    private final Integer priority;
    private final Long interval;
    private final Long editGracePeriod;
    private final JsonMap metadata;

    private ActionScheduleEdits(@NonNull Builder builder) {
        this.limit = builder.limit;
        this.start = builder.start;
        this.end = builder.end;
        this.actions = builder.actions;
        this.priority = builder.priority;
        this.editGracePeriod = builder.editGracePeriod;
        this.interval = builder.interval;
        this.metadata = builder.metadata;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Integer getLimit() {
        return limit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Integer getPriority() {
        return priority;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Long getStart() {
        return start;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Long getEnd() {
        return end;
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Long getInterval() {
        return interval;
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Long getEditGracePeriod() {
        return editGracePeriod;
    }

    @Nullable
    @Override
    public JsonMap getMetadata() {
        return metadata;
    }

    @Nullable
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public JsonValue getData() {
        return actions == null ? null : JsonValue.wrapOpt(actions);
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
     * Creates a new builder.
     *
     * @return A new builder instance.
     */
    @NonNull
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Parses a json value for an in-app message edits.
     *
     * @param value The json value.
     * @return The edit info.
     * @throws JsonException If the json is invalid.
     */
    @NonNull
    public static ActionScheduleEdits fromJson(@NonNull JsonValue value) throws JsonException {
        JsonMap jsonMap = value.optMap();

        Builder builder = newBuilder();

        if (jsonMap.containsKey(ActionScheduleInfo.ACTIONS_KEY)) {
            builder.setActions(jsonMap.opt(ActionScheduleInfo.ACTIONS_KEY).optMap().getMap());
        }

        if (jsonMap.containsKey(ActionScheduleInfo.LIMIT_KEY)) {
            builder.setLimit(jsonMap.opt(ActionScheduleInfo.LIMIT_KEY).getInt(1));
        }

        if (jsonMap.containsKey(ActionScheduleInfo.PRIORITY_KEY)) {
            builder.setPriority(jsonMap.opt(ActionScheduleInfo.PRIORITY_KEY).getInt(0));
        }

        if (jsonMap.containsKey(ActionScheduleInfo.END_KEY)) {
            try {
                builder.setEnd(DateUtils.parseIso8601(jsonMap.opt(ActionScheduleInfo.END_KEY).getString()));
            } catch (ParseException e) {
                throw new JsonException("Invalid schedule end time", e);
            }
        }

        if (jsonMap.containsKey(ActionScheduleInfo.START_KEY)) {
            try {
                builder.setStart(DateUtils.parseIso8601(jsonMap.opt(ActionScheduleInfo.START_KEY).getString()));
            } catch (ParseException e) {
                throw new JsonException("Invalid schedule start time", e);
            }
        }

        if (jsonMap.containsKey(ActionScheduleInfo.EDIT_GRACE_PERIOD)) {
            builder.setEditGracePeriod(jsonMap.opt(ActionScheduleInfo.EDIT_GRACE_PERIOD).getLong(0), TimeUnit.DAYS);
        }

        if (jsonMap.containsKey(ActionScheduleInfo.INTERVAL)) {
            builder.setInterval(jsonMap.opt(ActionScheduleInfo.INTERVAL).getLong(0), TimeUnit.SECONDS);
        }

        return builder.build();
    }

    /**
     * {@link ActionScheduleEdits} builder.
     */
    public static class Builder {

        private Integer limit;
        private Long start;
        private Long end;
        private Integer priority;
        private Long editGracePeriod;
        private Long interval;

        private Map<String, JsonValue> actions;
        private JsonMap metadata;

        private Builder() {
        }

        /**
         * Adds a map of actions.
         *
         * @param actionMap A map of action names to action values.
         * @return The Builder instance.
         */
        @NonNull
        public Builder setActions(@NonNull Map<String, JsonValue> actionMap) {
            actions = new HashMap<>(actionMap);
            return this;
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
         * Sets the start time in MS.
         *
         * @param start The start time in MS.
         * @return The Builder instance.
         */
        @NonNull
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
         * Builds the in-app message schedule edit.
         *
         * @return The schedule edit.
         */
        @NonNull
        public ActionScheduleEdits build() {
            return new ActionScheduleEdits(this);
        }

    }

}

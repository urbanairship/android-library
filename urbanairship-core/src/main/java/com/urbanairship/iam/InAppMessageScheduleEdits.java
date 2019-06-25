/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.automation.ScheduleEdits;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

import java.text.ParseException;
import java.util.concurrent.TimeUnit;

/**
 * Edits for an in-app message schedule.
 */
public class InAppMessageScheduleEdits implements ScheduleEdits {

    private final Integer limit;
    private final Long start;
    private final Long end;
    private final InAppMessage message;
    private final Integer priority;
    private final Long editGracePeriod;
    private final Long interval;
    private final JsonMap metadata;

    private InAppMessageScheduleEdits(@NonNull Builder builder) {
        this.limit = builder.limit;
        this.start = builder.start;
        this.end = builder.end;
        this.message = builder.message;
        this.priority = builder.priority;
        this.interval = builder.interval;
        this.editGracePeriod = builder.editGracePeriod;
        this.metadata = builder.metadata;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public JsonSerializable getData() {
        return message;
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

    /**
     * The message.
     *
     * @return The message.
     */
    @Nullable
    public InAppMessage getMessage() {
        return message;
    }

    /**
     * Parses a json value for in-app message edits.
     *
     * @param value The json value.
     * @return The edit info.
     * @throws JsonException If the json is invalid.
     */
    @NonNull
    public static InAppMessageScheduleEdits fromJson(@NonNull JsonValue value) throws JsonException {
        return fromJson(value, null);
    }

    /**
     * Parses a json value for in-app message edits.
     *
     * @param value The json value.
     * @param defaultSource The default source if it's not set in the JSON.
     * @return The edit info.
     * @throws JsonException If the json is invalid.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static InAppMessageScheduleEdits fromJson(@NonNull JsonValue value, @Nullable @InAppMessage.Source String defaultSource) throws JsonException {
        JsonMap jsonMap = value.optMap();

        Builder builder = newBuilder();

        if (jsonMap.containsKey(InAppMessageScheduleInfo.MESSAGE_KEY)) {
            builder.setMessage(InAppMessage.fromJson(jsonMap.opt(InAppMessageScheduleInfo.MESSAGE_KEY), defaultSource));
        }

        if (jsonMap.containsKey(InAppMessageScheduleInfo.LIMIT_KEY)) {
            builder.setLimit(jsonMap.opt(InAppMessageScheduleInfo.LIMIT_KEY).getInt(1));
        }

        if (jsonMap.containsKey(InAppMessageScheduleInfo.PRIORITY_KEY)) {
            builder.setPriority(jsonMap.opt(InAppMessageScheduleInfo.PRIORITY_KEY).getInt(0));
        }

        if (jsonMap.containsKey(InAppMessageScheduleInfo.END_KEY)) {
            try {
                builder.setEnd(DateUtils.parseIso8601(jsonMap.opt(InAppMessageScheduleInfo.END_KEY).getString()));
            } catch (ParseException e) {
                throw new JsonException("Invalid schedule end time", e);
            }
        }

        if (jsonMap.containsKey(InAppMessageScheduleInfo.START_KEY)) {
            try {
                builder.setStart(DateUtils.parseIso8601(jsonMap.opt(InAppMessageScheduleInfo.START_KEY).getString()));
            } catch (ParseException e) {
                throw new JsonException("Invalid schedule start time", e);
            }
        }

        if (jsonMap.containsKey(InAppMessageScheduleInfo.EDIT_GRACE_PERIOD)) {
            builder.setEditGracePeriod(jsonMap.opt(InAppMessageScheduleInfo.EDIT_GRACE_PERIOD).getLong(0), TimeUnit.DAYS);
        }

        if (jsonMap.containsKey(InAppMessageScheduleInfo.INTERVAL)) {
            builder.setInterval(jsonMap.opt(InAppMessageScheduleInfo.INTERVAL).getLong(0), TimeUnit.SECONDS);
        }

        return builder.build();
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
    public static Builder newBuilder(@NonNull InAppMessageScheduleEdits edits) {
        return new Builder(edits);
    }

    /**
     * {@link InAppMessageScheduleEdits} builder.
     */
    public static class Builder {

        private Integer limit;
        private Long start;
        private Long end;
        private InAppMessage message;
        private Integer priority;
        private Long editGracePeriod;
        private Long interval;
        private JsonMap metadata;

        private Builder() {
        }

        private Builder(@NonNull InAppMessageScheduleEdits edits) {
            this.limit = edits.limit;
            this.start = edits.start;
            this.end = edits.end;
            this.message = edits.message;
            this.priority = edits.priority;
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
         * Sets the in-app message.
         *
         * @param message The in-app message.
         * @return The builder instance.
         */
        @NonNull
        public Builder setMessage(@Nullable InAppMessage message) {
            this.message = message;
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
        public InAppMessageScheduleEdits build() {
            return new InAppMessageScheduleEdits(this);
        }

    }

}

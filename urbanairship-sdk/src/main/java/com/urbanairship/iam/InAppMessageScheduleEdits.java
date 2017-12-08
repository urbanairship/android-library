/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.automation.ScheduleEdits;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

import java.text.ParseException;

/**
 * Edits for an in-app message schedule.
 */
public class InAppMessageScheduleEdits implements ScheduleEdits {

    private final Integer limit;
    private final Long start;
    private final Long end;
    private final InAppMessage message;
    private final Integer priority;

    private InAppMessageScheduleEdits(Builder builder) {
        this.limit = builder.limit;
        this.start = builder.start;
        this.end = builder.end;
        this.message = builder.message;
        this.priority = builder.priority;
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
     * The message.
     *
     * @return The message.
     */
    @Nullable
    public InAppMessage getMessage() {
        return message;
    }


    /**
     * Parses a json value for an in-app message edits.
     *
     * @param value The json value.
     * @return The edit info.
     * @throws JsonException If the json is invalid.
     */
    public static InAppMessageScheduleEdits fromJson(@NonNull JsonValue value) throws JsonException {
        JsonMap jsonMap = value.optMap();

        Builder builder = newBuilder();

        if (jsonMap.containsKey(InAppMessageScheduleInfo.MESSAGE_KEY)) {
            builder.setMessage(InAppMessage.fromJson(jsonMap.opt(InAppMessageScheduleInfo.MESSAGE_KEY)));
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

        return builder.build();
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
     * Create a new builder that extends an edits instance.
     *
     * @param edits Edits to extend.
     * @return A new builder instance.
     */
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

        private Builder() {}

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
         * Builds the in-app message schedule edit.
         *
         * @return The schedule edit.
         */
        public InAppMessageScheduleEdits build() {
            return new InAppMessageScheduleEdits(this);
        }
    }
}

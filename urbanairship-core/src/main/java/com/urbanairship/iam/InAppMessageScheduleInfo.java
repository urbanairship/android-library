/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.automation.ScheduleDelay;
import com.urbanairship.automation.ScheduleInfo;
import com.urbanairship.automation.Trigger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.Checks;
import com.urbanairship.util.DateUtils;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Class encapsulating the implementor-set information for an in-app message schedule.
 */
public class InAppMessageScheduleInfo implements ScheduleInfo {

    /**
     * The triggers limit for a single schedule.
     */
    public static final long TRIGGER_LIMIT = 10;

    // JSON key
    static final String MESSAGE_KEY = "message";

    private final int limit;
    private final long start;
    private final long end;
    private final List<Trigger> triggers;
    private final ScheduleDelay delay;
    private final InAppMessage message;
    private final int priority;
    private final long editGracePeriod;
    private final long interval;

    /**
     * Default constructor.
     *
     * @param builder The schedule builder.
     */
    private InAppMessageScheduleInfo(@NonNull Builder builder) {
        this.limit = builder.limit;
        this.start = builder.start;
        this.end = builder.end;
        this.triggers = Collections.unmodifiableList(builder.triggers);
        this.delay = builder.delay;
        this.message = builder.message;
        this.priority = builder.priority;
        this.editGracePeriod = builder.editGracePeriod;
        this.interval = builder.interval;
    }

    /**
     * Gets the schedule's in-app message.
     *
     * @return The schedule's in-app message.
     */
    @NonNull
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
    @NonNull
    @Override
    public List<Trigger> getTriggers() {
        return triggers;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public JsonSerializable getData() {
        return message;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getGroup() {
        return message.getId();
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
     * Create a new builder.
     *
     * @return A new builder instance.
     */
    @NonNull
    public static Builder newBuilder(@NonNull InAppMessageScheduleInfo info) {
        return new Builder(info);
    }

    /**
     * Creates a schedule info from a json value.
     *
     * @param value The json value.
     * @param defaultSource The default source if not set in the JSON.
     * @return A schedule info.
     * @throws JsonException If the json value contains an invalid schedule info.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    static InAppMessageScheduleInfo fromJson(@NonNull JsonValue value, @Nullable @InAppMessage.Source String defaultSource) throws JsonException {
        JsonMap jsonMap = value.optMap();

        InAppMessageScheduleInfo.Builder builder = newBuilder()
                .setMessage(InAppMessage.fromJson(jsonMap.opt(MESSAGE_KEY), defaultSource))
                .setLimit(jsonMap.opt(LIMIT_KEY).getInt(1))
                .setPriority(jsonMap.opt(PRIORITY_KEY).getInt(0));

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
     * Creates a schedule info from a json value.
     *
     * @param value The json value.
     * @return A schedule info.
     * @throws JsonException If the json value contains an invalid schedule info.
     */
    @NonNull
    public static InAppMessageScheduleInfo fromJson(@NonNull JsonValue value) throws JsonException {
        return fromJson(value, null);
    }

    /**
     * Parses a message ID from a schedule info json value.
     *
     * @param jsonValue The json value.
     * @return The message ID or {@code null} if the message ID was unavailable.
     */
    @Nullable
    static String parseMessageId(@NonNull JsonValue jsonValue) {
        return jsonValue.optMap().opt(MESSAGE_KEY).optMap().opt(InAppMessage.MESSAGE_ID_KEY).getString();
    }

    /**
     * In-app message schedule info builder.
     */
    public static class Builder {

        private int limit = 1;
        private long start = -1;
        private long end = -1;
        private final List<Trigger> triggers = new ArrayList<>();
        private ScheduleDelay delay;
        private InAppMessage message;
        private int priority;
        private long editGracePeriod;
        private long interval;

        private Builder() {
        }

        private Builder(@NonNull InAppMessageScheduleInfo info) {
            this.limit = info.limit;
            this.start = info.start;
            this.end = info.end;
            this.triggers.addAll(info.triggers);
            this.delay = info.delay;
            this.message = info.message;
            this.priority = info.priority;
            this.editGracePeriod = info.editGracePeriod;
            this.interval = info.interval;
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
        public Builder setMessage(@NonNull InAppMessage message) {
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
         * Sets the list of triggers.
         *
         * @param triggers A list of trigger instances.
         * @return The Builder instance.
         */
        @NonNull
        public Builder setTriggers(@Nullable List<Trigger> triggers) {
            this.triggers.clear();
            if (triggers != null) {
                this.triggers.addAll(triggers);
            }
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
         * Builds the in-app message schedule.
         *
         * @return The new in-app message schedule.
         * @throws IllegalArgumentException if the no triggers are set, missing a valid in-app message,
         * {@link #TRIGGER_LIMIT} triggers are set, or the start time is set after the end time.
         */
        @NonNull
        public InAppMessageScheduleInfo build() {
            Checks.checkNotNull(message, "Missing message.");
            Checks.checkArgument(start < 0 || end < 0 || start <= end, "End must be on or after start.");
            Checks.checkArgument(triggers.size() > 0, "Must contain at least 1 trigger.");
            Checks.checkArgument(triggers.size() <= TRIGGER_LIMIT, "No more than " + TRIGGER_LIMIT + " triggers allowed.");
            return new InAppMessageScheduleInfo(this);
        }

    }

}

/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import com.urbanairship.audience.AudienceSelector;
import com.urbanairship.automation.actions.Actions;
import com.urbanairship.automation.deferred.Deferred;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.Checks;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.StringDef;
import androidx.core.util.ObjectsCompat;

/**
 * Schedule.
 */
public final class Schedule<T extends ScheduleData> {

    /**
     * Schedule types.
     *
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @StringDef({ TYPE_IN_APP_MESSAGE, TYPE_ACTION, TYPE_DEFERRED })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {}

    /**
     * Message in-app automation type.
     *
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static final String TYPE_IN_APP_MESSAGE = "in_app_message";

    /**
     * Actions in-app automation type.
     *
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static final String TYPE_ACTION = "actions";

    /**
     * Deferred in-app automation type.
     *
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static final String TYPE_DEFERRED = "deferred";

    /**
     * Default automation message type.
     *
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static final String DEFAULT_MESSAGE_TYPE = "transactional";

    /**
     * The triggers limit for a single schedule.
     */
    public static final long TRIGGER_LIMIT = 10;

    private final String id;
    private final JsonMap metadata;
    private final int limit;
    private final long start;
    private final long end;
    private final List<Trigger> triggers;
    private final ScheduleDelay delay;
    private final int priority;
    private final long triggeredTime;
    private final long editGracePeriod;
    private final long interval;
    private final String group;
    private final AudienceSelector audience;
    private final JsonValue campaigns;
    private final JsonValue reportingContext;
    private final List<String> frequencyConstraintIds;
    private final String messageType;
    private final boolean bypassHoldoutGroups;
    private final long newUserEvaluationDate;

    @Type
    private final String type;
    private final T data;

    /**
     * Default constructor.
     *
     * @param builder The schedule builder.
     */
    private Schedule(@NonNull Builder<T> builder) {
        this.id = builder.id == null ? UUID.randomUUID().toString() : builder.id;
        this.metadata = builder.metadata == null ? JsonMap.EMPTY_MAP : builder.metadata;
        this.limit = builder.limit;
        this.start = builder.start;
        this.end = builder.end;
        this.triggers = Collections.unmodifiableList(builder.triggers);
        this.delay = builder.delay == null ? ScheduleDelay.newBuilder().build() : builder.delay;
        this.priority = builder.priority;
        this.triggeredTime = builder.triggeredTime;
        this.editGracePeriod = builder.editGracePeriod;
        this.interval = builder.interval;
        this.data = builder.data;
        this.type = builder.type;
        this.group = builder.group;
        this.audience = builder.audienceSelector;
        this.campaigns = builder.campaigns == null ? JsonValue.NULL : builder.campaigns;
        this.reportingContext = builder.reportingContext == null ? JsonValue.NULL : builder.reportingContext;
        this.frequencyConstraintIds = builder.frequencyConstraintIds == null ? Collections.<String>emptyList() : Collections.unmodifiableList(builder.frequencyConstraintIds);
        this.messageType = builder.messageType == null ? DEFAULT_MESSAGE_TYPE : builder.messageType;
        this.bypassHoldoutGroups = builder.bypassHoldoutGroups == null ? false : builder.bypassHoldoutGroups;
        this.newUserEvaluationDate = builder.newUserEvaluationDate;
    }

    /**
     * Gets the schedule ID.
     *
     * @return The schedule ID.
     */
    @NonNull
    public String getId() {
        return id;
    }

    /**
     * Gets the metadata.
     *
     * @return The metadata.
     */
    @NonNull
    public JsonMap getMetadata() {
        return metadata;
    }

    /**
     * Gets the audience.
     *
     * @return The audience.
     */
    @Nullable
    public Audience getAudience() {
        if (audience == null) {
            return null;
        }
        return new Audience(audience);
    }

    /**
     * Gets the audience.
     *
     * @return The audience.
     * @hide
     */
    @Nullable
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public AudienceSelector getAudienceSelector() {
        return audience;
    }

    /**
     * Gets the schedule type.
     *
     * @return The schedule type.
     */
    @Type
    public String getType() {
        return type;
    }

    /**
     * Gets the action triggers.
     *
     * @return A list of triggers.
     */
    @NonNull
    public List<Trigger> getTriggers() {
        return triggers;
    }

    /**
     * Gets the schedule data, coerced to the specified type.
     *
     * @return Schedule data, coerced to the specified type.
     */
    public T getData() {
        return data;
    }

    /**
     * The campaigns info.
     *
     * @return The campaigns info.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public JsonValue getCampaigns() {
        return campaigns;
    }

    /**
     * The reporting context.
     *
     * @return The reporting context.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public JsonValue getReportingContext() {
        return reportingContext;
    }

    /**
     * The frequency constraint Ids.
     *
     * @return The constraint Ids.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public List<String> getFrequencyConstraintIds() {
        return frequencyConstraintIds;
    }

    /**
     * The automation message type.
     *
     * @return The message type.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public String getMessageType() {
        return messageType;
    }

    /**
     * Whether the schedule could be in a holdout group.
     *
     * @return Whether the schedule could be in a holdout group.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public boolean isBypassHoldoutGroups() {
        return bypassHoldoutGroups;
    }

    /**
     * The schedule created date
     *
     * @return unix timestamp.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public long getNewUserEvaluationDate() {
        return newUserEvaluationDate;
    }

    /**
     * Gets the data.
     *
     * If the data is the wrong type, an {@link IllegalArgumentException} will be thrown.
     *
     * @return Schedule data.
     * @hide
     */
    @NonNull
    @SuppressWarnings("unchecked")
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public <S extends ScheduleData> S coerceType() {
        try {
            return (S) data;
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Unexpected data", e);
        }
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
     * Gets the schedule priority level.
     *
     * @return The priority level.
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Gets the schedule triggered time in ms.
     *
     * @return The triggered time in ms.
     */
    public long getTriggeredTime() {
        return triggeredTime;
    }

    /**
     * Gets the schedule group.
     *
     * @return The schedule group.
     */
    @Nullable
    public String getGroup() {
        return group;
    }

    /**
     * Gets the schedule start time in ms.
     *
     * @return The schedule start time in ms.
     */
    public long getStart() {
        return start;
    }

    /**
     * Gets the schedule end time in ms.
     *
     * @return The schedule end time in ms.
     */
    public long getEnd() {
        return end;
    }

    /**
     * Gets the schedule's delay.
     *
     * @return A ScheduleDelay instance.
     */
    @Nullable
    public ScheduleDelay getDelay() {
        return delay;
    }

    /**
     * Gets the edit grace period in ms.
     *
     * @return The edit grace period in ms.
     */
    public long getEditGracePeriod() {
        return editGracePeriod;
    }

    /**
     * Gets the schedule execution interval in ms.
     *
     * @return The interval in ms.
     */
    public long getInterval() {
        return interval;
    }

    /**
     * Gets the schedule data as JSON.
     *
     * @return The schedule data as a JSON value.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    JsonValue getDataAsJson() {
        return data.toJsonValue();
    }

    /**
     * Create a new builder for an action schedule.
     *
     * @param actions The actions.
     * @return A new builder instance.
     */
    @NonNull
    public static Builder<Actions> newBuilder(@NonNull Actions actions) {
        return new Builder<>(TYPE_ACTION, actions);
    }

    /**
     * Create a new builder for an in-app message schedule.
     *
     * @param message The message.
     * @return A new builder instance.
     */
    @NonNull
    public static Builder<InAppMessage> newBuilder(@NonNull InAppMessage message) {
        return new Builder<>(TYPE_IN_APP_MESSAGE, message);
    }

    /**
     * Create a new builder for a deferred schedule.
     *
     * @param deferred The deferred schedule data.
     * @return A new builder instance.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    static Builder<Deferred> newBuilder(Deferred deferred) {
        return new Builder<>(TYPE_DEFERRED, deferred);
    }

    /**
     * Create a new builder.
     *
     * @param schedule The schedule to extend.
     * @return A new builder instance.
     */
    @NonNull
    public static <T extends ScheduleData> Builder<T> newBuilder(@NonNull Schedule<T> schedule) {
        return new Builder<>(schedule);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Schedule<?> schedule = (Schedule<?>) o;

        if (limit != schedule.limit) return false;
        if (start != schedule.start) return false;
        if (end != schedule.end) return false;
        if (priority != schedule.priority) return false;
        if (triggeredTime != schedule.triggeredTime) return false;
        if (editGracePeriod != schedule.editGracePeriod) return false;
        if (interval != schedule.interval) return false;
        if (!id.equals(schedule.id)) return false;
        if (metadata != null ? !metadata.equals(schedule.metadata) : schedule.metadata != null)
            return false;
        if (!triggers.equals(schedule.triggers)) return false;
        if (delay != null ? !delay.equals(schedule.delay) : schedule.delay != null) return false;
        if (group != null ? !group.equals(schedule.group) : schedule.group != null) return false;
        if (audience != null ? !audience.equals(schedule.audience) : schedule.audience != null)
            return false;
        if (campaigns != null ? !campaigns.equals(schedule.campaigns) : schedule.campaigns != null)
            return false;

        if (!ObjectsCompat.equals(reportingContext, schedule.reportingContext)) {
            return false;
        }

        if (frequencyConstraintIds != null ? !frequencyConstraintIds.equals(schedule.frequencyConstraintIds) : schedule.frequencyConstraintIds != null)
            return false;
        if (!type.equals(schedule.type)) return false;

        if (!ObjectsCompat.equals(messageType, schedule.messageType)) {
            return false;
        }

        if (bypassHoldoutGroups != schedule.bypassHoldoutGroups) {
            return false;
        }

        return data.equals(schedule.data);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + (metadata != null ? metadata.hashCode() : 0);
        result = 31 * result + limit;
        result = 31 * result + (int) (start ^ (start >>> 32));
        result = 31 * result + (int) (end ^ (end >>> 32));
        result = 31 * result + triggers.hashCode();
        result = 31 * result + (delay != null ? delay.hashCode() : 0);
        result = 31 * result + priority;
        result = 31 * result + (int) (triggeredTime ^ (triggeredTime >>> 32));
        result = 31 * result + (int) (editGracePeriod ^ (editGracePeriod >>> 32));
        result = 31 * result + (int) (interval ^ (interval >>> 32));
        result = 31 * result + (group != null ? group.hashCode() : 0);
        result = 31 * result + (audience != null ? audience.hashCode() : 0);
        result = 31 * result + (campaigns != null ? campaigns.hashCode() : 0);
        result = 31 * result + (frequencyConstraintIds != null ? frequencyConstraintIds.hashCode() : 0);
        result = 31 * result + type.hashCode();
        result = 31 * result + data.hashCode();
        result = 31 * result + reportingContext.hashCode();
        return result;
    }

    @Override
    @NonNull
    public String toString() {
        return "Schedule{" +
                "id='" + id + '\'' +
                ", metadata=" + metadata +
                ", limit=" + limit +
                ", start=" + start +
                ", end=" + end +
                ", triggers=" + triggers +
                ", delay=" + delay +
                ", priority=" + priority +
                ", triggeredTime=" + triggeredTime +
                ", editGracePeriod=" + editGracePeriod +
                ", interval=" + interval +
                ", group='" + group + '\'' +
                ", audience=" + audience +
                ", type='" + type + '\'' +
                ", data=" + data +
                ", campaigns=" + campaigns +
                ", reportingContext=" + reportingContext +
                ", frequencyConstraintIds=" + frequencyConstraintIds +
                ", newUserEvaluationDate=" + newUserEvaluationDate +
                '}';
    }

    /**
     * In-app message schedule info builder.
     */
    public static class Builder<T extends ScheduleData> {

        private int limit = 1;
        private long start = -1;
        private long end = -1;
        private final List<Trigger> triggers = new ArrayList<>();
        private ScheduleDelay delay;
        private int priority;
        private long triggeredTime = -1;
        private long editGracePeriod;
        private long interval;
        private T data;
        @Type
        private String type;
        private String group;
        private JsonMap metadata;
        private String id;
        private AudienceSelector audienceSelector;
        private JsonValue campaigns;
        private JsonValue reportingContext;
        private List<String> frequencyConstraintIds;
        private String messageType;
        private Boolean bypassHoldoutGroups = false;
        private long newUserEvaluationDate;

        private Builder(@NonNull Schedule<T> info) {
            this.id = info.id;
            this.metadata = info.metadata == null ? JsonMap.EMPTY_MAP : info.metadata;
            this.limit = info.limit;
            this.start = info.start;
            this.end = info.end;
            this.triggers.addAll(info.triggers);
            this.delay = info.delay;
            this.data = info.data;
            this.type = info.type;
            this.priority = info.priority;
            this.triggeredTime = info.triggeredTime;
            this.editGracePeriod = info.editGracePeriod;
            this.interval = info.interval;
            this.audienceSelector = info.audience;
            this.group = info.group;
            this.campaigns = info.campaigns;
            this.frequencyConstraintIds = info.frequencyConstraintIds;
            this.reportingContext = info.reportingContext;
            this.messageType = info.messageType;
            this.bypassHoldoutGroups = info.bypassHoldoutGroups;
            this.newUserEvaluationDate = info.newUserEvaluationDate;
        }

        private Builder(@NonNull @Type String type, @NonNull T data) {
            this.type = type;
            this.data = data;
        }

        /**
         * Sets the audience.
         *
         * @param audience The audience.
         * @return The builder.
         */
        @NonNull
        @Deprecated
        public Builder<T> setAudience(@Nullable Audience audience) {
            // no usage
            this.audienceSelector = audience == null ? null : audience.getAudienceSelector();
            return this;
        }

        /**
         * Sets the audience.
         *
         * @param audience The audience.
         * @return The builder.
         */
        @NonNull
        public Builder<T> setAudience(@Nullable AudienceSelector audience) {
            this.audienceSelector = audience;
            return this;
        }

        /**
         * Sets the schedule ID.
         *
         * @param id The schedule ID.
         * @return The builder instance.
         */
        @NonNull
        public Builder<T> setId(@NonNull String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the metadata.
         *
         * @param metadata The metadata.
         * @return The builder instance.
         */
        @NonNull
        public Builder<T> setMetadata(@NonNull JsonMap metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Sets the display limit.
         *
         * @param limit The display limit.
         * @return The builder instance.
         */
        @NonNull
        public Builder<T> setLimit(int limit) {
            this.limit = limit;
            return this;
        }

        /**
         * Sets the start time in ms.
         *
         * @param start The start time in ms.
         * @return The Builder instance.
         */
        @NonNull
        public Builder<T> setStart(long start) {
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
        public Builder<T> setEnd(long end) {
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
        public Builder<T> setDelay(@Nullable ScheduleDelay delay) {
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
        public Builder<T> setPriority(int priority) {
            this.priority = priority;
            return this;
        }

        /**
         * Sets the triggered time in ms.
         *
         * @param triggeredTime The triggered time in ms.
         * @return The Builder instance.
         * @hide
         */
        @NonNull
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public Builder<T> setTriggeredTime(long triggeredTime) {
            this.triggeredTime = triggeredTime;
            return this;
        }

        /**
         * Sets the group.
         *
         * @param group The group.
         * @return The Builder instance.
         */
        @NonNull
        public Builder<T> setGroup(@Nullable String group) {
            this.group = group;
            return this;
        }

        /**
         * Adds a trigger.
         *
         * @param trigger A trigger instance.
         * @return The Builder instance.
         */
        @NonNull
        public Builder<T> addTrigger(@NonNull Trigger trigger) {
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
        public Builder<T> addTriggers(@NonNull List<Trigger> triggers) {
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
        public Builder<T> setTriggers(@Nullable List<Trigger> triggers) {
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
        public Builder<T> setEditGracePeriod(@IntRange(from = 0) long duration, @NonNull TimeUnit timeUnit) {
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
        public Builder<T> setInterval(@IntRange(from = 0) long duration, @NonNull TimeUnit timeUnit) {
            this.interval = timeUnit.toMillis(duration);
            return this;
        }

        /**
         * Sets the reporting context.
         *
         * @param reportingContext The reporting context.
         * @return The Builder instance.
         * @hide
         */
        @NonNull
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public Builder<T> setReportingContext(@Nullable JsonValue reportingContext) {
            this.reportingContext = reportingContext;
            return this;
        }

        /**
         * Sets the campaigns info.
         *
         * @param campaigns The campaigns info.
         * @return The Builder instance.
         * @hide
         */
        @NonNull
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public Builder<T> setCampaigns(@Nullable JsonValue campaigns) {
            this.campaigns = campaigns;
            return this;
        }

        /**
         * Sets the frequency constraint Ids.
         *
         * @param frequencyConstraintIds The constraint Ids.
         * @return The Builder instance.
         * @hide
         */
        @NonNull
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public Builder<T> setFrequencyConstraintIds(@Nullable List<String> frequencyConstraintIds) {
            this.frequencyConstraintIds = frequencyConstraintIds;
            return this;
        }

        /**
         * Sets the message type.
         *
         * @param messageType The message type.
         * @return The Builder instance.
         * @hide
         */
        @NonNull
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public Builder<T> setMessageType(@Nullable String messageType) {
            this.messageType = messageType;
            return this;
        }

        /**
         * Sets whether or not the schedule could be in a holdout group.
         *
         * @param bypassHoldoutGroups The property value.
         * @return The Builder instance.
         * @hide
         */
        @NonNull
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public Builder<T> setBypassHoldoutGroups(Boolean bypassHoldoutGroups) {
            this.bypassHoldoutGroups = bypassHoldoutGroups;
            return this;
        }

        /**
         * Sets the created date for this schedule we can use in IAA audience check
         *
         * @param date The property value.
         * @return The Builder instance.
         * @hide
         */
        @NonNull
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public Builder<T> setNewUserEvaluationDate(long date) {
            this.newUserEvaluationDate = date;
            return this;
        }

        /**
         * Builds the in-app message schedule.
         *
         * @return The new in-app message schedule.
         * @throws IllegalArgumentException if no triggers are set, {@link #TRIGGER_LIMIT} triggers are set,
         * or the start time is set after the end time.
         */
        @NonNull
        public Schedule<T> build() {
            Checks.checkNotNull(data, "Missing data.");
            Checks.checkNotNull(type, "Missing type.");
            Checks.checkArgument(start < 0 || end < 0 || start <= end, "End must be on or after start.");
            Checks.checkArgument(triggers.size() > 0, "Must contain at least 1 trigger.");
            Checks.checkArgument(triggers.size() <= TRIGGER_LIMIT, "No more than " + TRIGGER_LIMIT + " triggers allowed.");
            return new Schedule<T>(this);
        }
    }
}

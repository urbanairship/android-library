/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import com.urbanairship.audience.AudienceSelector;
import com.urbanairship.automation.actions.Actions;
import com.urbanairship.automation.deferred.Deferred;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Schedule edits.
 */
public class ScheduleEdits<T extends ScheduleData> {

    private final Integer limit;
    private final Long start;
    private final Long end;
    private final T data;
    private final Integer priority;
    private final Long editGracePeriod;
    private final Long interval;
    private final JsonMap metadata;
    private final AudienceSelector audienceSelector;
    @Schedule.Type
    private final String type;
    private final JsonValue campaigns;
    private final JsonValue reportingContext;
    private final List<String> frequencyConstraintIds;
    private final String messageType;
    private final Boolean bypassHoldoutGroups;
    private final long newUserEvaluationDate;

    private ScheduleEdits(@NonNull Builder<T> builder) {
        this.limit = builder.limit;
        this.start = builder.start;
        this.end = builder.end;
        this.data = builder.data;
        this.type = builder.type;
        this.priority = builder.priority;
        this.interval = builder.interval;
        this.editGracePeriod = builder.editGracePeriod;
        this.metadata = builder.metadata;
        this.frequencyConstraintIds = builder.frequencyConstraintIds;
        this.campaigns = builder.campaigns;
        this.reportingContext = builder.reportingContext;
        this.messageType = builder.messageType;
        this.bypassHoldoutGroups = builder.bypassHoldoutGroups;
        this.audienceSelector = builder.audienceSelector;
        this.newUserEvaluationDate = builder.newUserEvaluationDate;
    }

    /**
     * Gets the schedule data.
     *
     * @return Schedule data.
     * @hide
     */
    @Nullable
    public T getData() {
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
     * @hide
     */
    @Schedule.Type
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Nullable
    public String getType() {
        return type;
    }

    /**
     * Gets the schedule's audience edits.
     *
     * @return The schedule's audience edits.
     * @hide
     */
    @Nullable
    public Audience getAudience() {
        // no internal usage
        if (audienceSelector == null) {
            return null;
        }
        return new Audience(audienceSelector);
    }

    /**
     * Gets the schedule's audience edits.
     *
     * @return The schedule's audience edits.
     * @hide
     */
    @Nullable
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public AudienceSelector getAudienceSelector() {
        return audienceSelector;
    }

    /**
     * The campaigns info.
     *
     * @return The campaigns info.
     * @hide
     */
    @Nullable
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
    @Nullable
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
    @Nullable
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public List<String> getFrequencyConstraintIds() {
        return frequencyConstraintIds;
    }

    /**
     * Get bypassHoldoutGroup property.
     *
     * @return The bypassHoldoutGroup flag.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public Boolean getBypassHoldoutGroup() {
        return bypassHoldoutGroups;
    }

    /**
     * Get newUserEvaluationDate property.
     *
     * @return The newUserEvaluationDate timestamp.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public long getNewUserEvaluationDate() {
        return newUserEvaluationDate;
    }

    /**
     * The message type.
     *
     * @return The message type.
     * @hide
     */
    @Nullable
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public String getMessageType() {
        return messageType;
    }

    /**
     * Create a new builder that extends an edits instance.
     *
     * @return A new builder instance.
     */
    @NonNull
    public static Builder<?> newBuilder() {
        return new Builder<>();
    }

    /**
     * Create a new builder that edits the schedule type as actions.
     *
     * @param actions The actions.
     * @return A new builder instance.
     */
    @NonNull
    public static Builder<Actions> newBuilder(@NonNull Actions actions) {
        return new Builder<>(Schedule.TYPE_ACTION, actions);
    }

    /**
     * Create a new builder that edits the schedule type as an in-app message.
     *
     * @param message The in-app message.
     * @return A new builder instance.
     */
    @NonNull
    public static Builder<InAppMessage> newBuilder(@NonNull InAppMessage message) {
        return new Builder<>(Schedule.TYPE_IN_APP_MESSAGE, message);
    }

    /**
     * Create a new builder that edits the schedule type as deferred.
     *
     * @param deferred The deferred data.
     * @return A new builder instance.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static Builder<Deferred> newBuilder(@NonNull Deferred deferred) {
        return new Builder<>(Schedule.TYPE_DEFERRED, deferred);
    }

    /**
     * Create a new builder that extends an edits instance.
     *
     * @param edits Edits to extend.
     * @return A new builder instance.
     */
    @NonNull
    public static <T extends ScheduleData> Builder<T> newBuilder(@NonNull ScheduleEdits<T> edits) {
        return new Builder<>(edits);
    }


    /**
     * {@link ScheduleEdits} builder.
     */
    public static class Builder<T extends ScheduleData> {

        private Integer limit;
        private Long start;
        private Long end;
        private Integer priority;
        private Long editGracePeriod;
        private Long interval;
        private JsonMap metadata;
        private T data;
        private JsonValue campaigns;
        private JsonValue reportingContext;
        private List<String> frequencyConstraintIds;
        private String messageType;
        private Boolean bypassHoldoutGroups;
        private long newUserEvaluationDate;

        @Schedule.Type
        private String type;
        private AudienceSelector audienceSelector;

        private Builder() {
        }

        private Builder(@NonNull @Schedule.Type String type, @NonNull T scheduleData) {
            this.type = type;
            this.data = scheduleData;
        }

        private Builder(@NonNull ScheduleEdits<T> edits) {
            this.limit = edits.limit;
            this.start = edits.start;
            this.end = edits.end;
            this.data = edits.data;
            this.priority = edits.priority;
            this.type = edits.type;
            this.editGracePeriod = edits.editGracePeriod;
            this.interval = edits.interval;
            this.metadata = edits.metadata;
            this.campaigns = edits.campaigns;
            this.frequencyConstraintIds = edits.frequencyConstraintIds;
            this.reportingContext = edits.reportingContext;
            this.messageType = edits.messageType;
            this.bypassHoldoutGroups = edits.bypassHoldoutGroups;
            this.newUserEvaluationDate = edits.newUserEvaluationDate;
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
         * Sets the metadata.
         *
         * @param metadata The metadata.
         * @return The Builder instance.
         */
        @NonNull
        public Builder<T> setMetadata(@Nullable JsonMap metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Sets the audience.
         *
         * @param audience The audience.
         * @return The builder instance.
         */
        @Deprecated
        public Builder<T> setAudience(@Nullable Audience audience) {
            this.audienceSelector = audience == null ? null : audience.getAudienceSelector();
            return this;
        }

        /**
         * Sets the audience.
         *
         * @param audience The audience.
         * @return The builder instance.
         */
        public Builder<T> setAudience(@Nullable AudienceSelector audience) {
            this.audienceSelector = audience;
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
         * Sets the frequency constraint Ids.
         *
         * @param frequencyConstraintIds The constraint Ids.
         * @return The Builder instance.
         * @hide
         */
        @NonNull
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public Builder<T> setFrequencyConstraintIds(@Nullable List<String> frequencyConstraintIds) {
            this.frequencyConstraintIds = frequencyConstraintIds == null ? null : new ArrayList<>(frequencyConstraintIds);
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
         * Sets the bypassHoldoutGroup property.
         *
         * @param flag The bypassHoldoutGroup property.
         * @return The Builder instance.
         * @hide
         */
        @NonNull
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public Builder<T> setBypassHoldoutGroup(@Nullable Boolean flag) {
            this.bypassHoldoutGroups = flag;
            return this;
        }

        /**
         * Sets the newUserEvaluationDate property.
         *
         * @param date The newUserEvaluationDate property.
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
         * Builds the in-app message schedule edits.
         *
         * @return The schedule edits.
         */
        @NonNull
        public ScheduleEdits<T> build() {
            return new ScheduleEdits<>(this);
        }

    }

}

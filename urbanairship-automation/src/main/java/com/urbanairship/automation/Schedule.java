/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.os.Parcel;
import android.os.Parcelable;

import com.urbanairship.iam.InAppMessage;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
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

/**
 * Schedule info.
 */
public class Schedule implements Parcelable {

    public static final Creator<Schedule> CREATOR = new Creator<Schedule>() {
        @Override
        public Schedule createFromParcel(Parcel in) {
            return new Schedule(in);
        }

        @Override
        public Schedule[] newArray(int size) {
            return new Schedule[size];
        }
    };

    @StringDef({ TYPE_IN_APP_MESSAGE, TYPE_ACTION, TYPE_DEFERRED })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {}

    /**
     * Message in-app automation type.
     */
    @NonNull
    public static final String TYPE_IN_APP_MESSAGE = "in_app_message";

    /**
     * Actions in-app automation type.
     */
    @NonNull
    public static final String TYPE_ACTION = "actions";

    /**
     * Deferred in-app automation type.
     */
    @NonNull
    public static final String TYPE_DEFERRED = "deferred";

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
    private final long editGracePeriod;
    private final long interval;
    private final String group;
    private final Audience audience;

    @Type
    private final String type;
    private final JsonSerializable data;

    /**
     * Default constructor.
     *
     * @param builder The schedule builder.
     */
    private Schedule(@NonNull Builder builder) {
        this.id = builder.id == null ? UUID.randomUUID().toString() : builder.id;
        this.metadata = builder.metadata == null ? JsonMap.EMPTY_MAP : builder.metadata;
        this.limit = builder.limit;
        this.start = builder.start;
        this.end = builder.end;
        this.triggers = Collections.unmodifiableList(builder.triggers);
        this.delay = builder.delay == null ? ScheduleDelay.newBuilder().build() : builder.delay;
        this.priority = builder.priority;
        this.editGracePeriod = builder.editGracePeriod;
        this.interval = builder.interval;
        this.data = builder.data;
        this.type = builder.type;
        this.group = builder.group;
        this.audience = builder.audience;
    }

    private Schedule(Parcel in) {
        this.id = in.readString();
        JsonMap parsedMetadata;
        try {
            parsedMetadata = JsonValue.parseString(in.readString()).optMap();
        } catch (JsonException e) {
            throw new IllegalArgumentException("Unexpected metadata", e);
        }

        this.metadata = parsedMetadata;

        this.triggers = in.createTypedArrayList(Trigger.CREATOR);
        this.limit = in.readInt();
        this.priority = in.readInt();
        this.group = in.readString();
        this.start = in.readLong();
        this.end = in.readLong();
        this.editGracePeriod = in.readLong();
        this.interval = in.readLong();
        this.delay = in.readParcelable(ScheduleDelay.class.getClassLoader());
        try {
            String type = in.readString();
            Checks.checkNotNull(type, "Missing type");
            this.type = parseType(type);
            this.data = parseData(JsonValue.parseString(in.readString()), type);
        } catch (JsonException e) {
            throw new IllegalArgumentException("Unexpected type and data", e);
        }

        String audienceJson = in.readString();
        if (audienceJson != null) {
            try {
                this.audience = Audience.fromJson(JsonValue.parseString(audienceJson));
            } catch (JsonException e) {
                throw new IllegalArgumentException("Unexpected audience", e);
            }
        } else {
            this.audience = null;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(metadata.toString());
        dest.writeTypedList(triggers);
        dest.writeInt(limit);
        dest.writeInt(priority);
        dest.writeString(group);
        dest.writeLong(start);
        dest.writeLong(end);
        dest.writeLong(editGracePeriod);
        dest.writeLong(interval);
        dest.writeParcelable(delay, flags);
        dest.writeString(type);
        dest.writeParcelable(JsonValue.wrapOpt(data), flags);
        dest.writeString(audience == null ? null : audience.toJsonValue().toString());
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
     * Gets the schedule data.
     *
     * @return Schedule data or null if the type is mismatched.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T getData() {
        try {
            return (T) data;
        } catch (ClassCastException e) {
            return null;
        }
    }

    /**
     * Gets the data.
     *
     * If the data is the wrong type, an {@link IllegalArgumentException} will be thrown..
     *
     * @return Schedule data.
     */
    @NonNull
    public <T> T requireData() {
        T data = getData();
        if (data == null) {
            throw new IllegalArgumentException("Unexpected data");
        }

        return data;
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
     * @return The schedule data as a JSON value.
     *
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
     * @return A new builder instance.
     */
    @NonNull
    public static Builder newActionScheduleBuilder(@NonNull JsonMap actions) {
        return new Builder(TYPE_ACTION, actions);
    }

    /**
     * Create a new builder for an in-app message schedule.
     *
     * @return A new builder instance.
     */
    @NonNull
    public static Builder newMessageScheduleBuilder(@NonNull InAppMessage message) {
        return new Builder(TYPE_IN_APP_MESSAGE, message);
    }

    /**
     * Create a new builder.
     *
     * @param schedule The schedule to extend.
     * @return A new builder instance.
     */
    @NonNull
    public static Builder newBuilder(@NonNull Schedule schedule) {
        return new Builder(schedule);
    }

    /**
     * Creates a builder with a given type and value.
     *
     * @param type The schedule type.
     * @param json The JSON value.
     * @return The builder.
     * @throws JsonException If the value or type is invalid.
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    static Builder newBuilder(@NonNull String type, @NonNull JsonValue json) throws JsonException {
        @Type String parsedType = parseType(type);
        return new Builder(parsedType, parseData(json, parsedType));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Schedule schedule = (Schedule) o;

        if (limit != schedule.limit) return false;
        if (start != schedule.start) return false;
        if (end != schedule.end) return false;
        if (priority != schedule.priority) return false;
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
        if (!type.equals(schedule.type)) return false;
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
        result = 31 * result + (int) (editGracePeriod ^ (editGracePeriod >>> 32));
        result = 31 * result + (int) (interval ^ (interval >>> 32));
        result = 31 * result + (group != null ? group.hashCode() : 0);
        result = 31 * result + (audience != null ? audience.hashCode() : 0);
        result = 31 * result + type.hashCode();
        result = 31 * result + data.hashCode();
        return result;
    }

    @Override
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
                ", editGracePeriod=" + editGracePeriod +
                ", interval=" + interval +
                ", group='" + group + '\'' +
                ", audience=" + audience +
                ", type='" + type + '\'' +
                ", data=" + data +
                '}';
    }

    @Type
    private static String parseType(@NonNull String type) throws JsonException {
        switch (type) {
            case TYPE_ACTION:
                return TYPE_ACTION;
            case TYPE_IN_APP_MESSAGE:
                return TYPE_IN_APP_MESSAGE;
            case TYPE_DEFERRED:
                return TYPE_DEFERRED;
        }

        throw new JsonException("Invalid type: " + type);
    }

    private static JsonSerializable parseData(@NonNull JsonValue json, @Schedule.Type String type) throws JsonException {
        switch (type) {
            case Schedule.TYPE_ACTION:
                return json.optMap();
            case Schedule.TYPE_IN_APP_MESSAGE:
                return InAppMessage.fromJson(json);
            case Schedule.TYPE_DEFERRED:
                return DeferredScheduleData.fromJson(json);
        }

        throw new JsonException("Invalid type: " + type);
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
        private int priority;
        private long editGracePeriod;
        private long interval;
        private JsonSerializable data;
        @Type
        private String type;
        private String group;
        private JsonMap metadata;
        private String id;
        private Audience audience;

        private Builder(@NonNull Schedule info) {
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
            this.editGracePeriod = info.editGracePeriod;
            this.interval = info.interval;
            this.audience = info.audience;
        }

        private Builder(@NonNull @Type String type, @NonNull JsonSerializable data) {
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
        public Builder setAudience(@Nullable Audience audience) {
            this.audience = audience;
            return this;
        }

        /**
         * Sets the schedule ID.
         *
         * @param id The schedule ID.
         * @return The builder instance.
         */
        @NonNull
        public Builder setId(@NonNull String id) {
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
        public Builder setMetadata(@NonNull JsonMap metadata) {
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
        public Builder setLimit(int limit) {
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
         * Sets the group.
         *
         * @param group The group.
         * @return The Builder instance.
         */
        @NonNull
        public Builder setGroup(@Nullable String group) {
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
         * @throws IllegalArgumentException if no triggers are set, {@link #TRIGGER_LIMIT} triggers are set,
         * or the start time is set after the end time.
         */
        @NonNull
        public Schedule build() {
            Checks.checkNotNull(data, "Missing data.");
            Checks.checkNotNull(type, "Missing type.");
            Checks.checkArgument(start < 0 || end < 0 || start <= end, "End must be on or after start.");
            Checks.checkArgument(triggers.size() > 0, "Must contain at least 1 trigger.");
            Checks.checkArgument(triggers.size() <= TRIGGER_LIMIT, "No more than " + TRIGGER_LIMIT + " triggers allowed.");
            return new Schedule(this);
        }

    }

}

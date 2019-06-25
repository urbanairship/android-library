/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonPredicate;
import com.urbanairship.json.JsonValue;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

/**
 * Trigger defines a condition to execute an {@link ScheduleInfo}. Use {@link Triggers} to build
 * triggers.
 */
public class Trigger implements Parcelable {

    // JSON
    private static final String GOAL_KEY = "goal";
    private static final String TYPE_KEY = "type";
    private static final String PREDICATE_KEY = "predicate";
    private static final String CUSTOM_EVENT_COUNT_NAME = "custom_event_count";
    private static final String CUSTOM_EVENT_VALUE_NAME = "custom_event_value";
    private static final String FOREGROUND_NAME = "foreground";
    private static final String BACKGROUND_NAME = "background";
    private static final String APP_INIT_NAME = "app_init";
    private static final String SCREEN_NAME = "screen";
    private static final String REGION_ENTER_NAME = "region_enter";
    private static final String REGION_EXIT_NAME = "region_exit";
    private static final String ACTIVE_SESSION_NAME = "active_session";
    private static final String VERSION_NAME = "version";

    @IntDef({ LIFE_CYCLE_FOREGROUND, LIFE_CYCLE_BACKGROUND, REGION_ENTER, REGION_EXIT,
            CUSTOM_EVENT_COUNT, CUSTOM_EVENT_VALUE, SCREEN_VIEW, LIFE_CYCLE_APP_INIT,
            ACTIVE_SESSION, VERSION })
    @Retention(RetentionPolicy.SOURCE)
    public @interface TriggerType {}

    /**
     * Trigger type for foreground events. Foreground triggers
     * can be created with {@link Triggers#newForegroundTriggerBuilder()}
     */
    public static final int LIFE_CYCLE_FOREGROUND = 1;

    /**
     * Trigger type for background events. Background triggers
     * can be created with {@link Triggers#newBackgroundTriggerBuilder()}
     */
    public static final int LIFE_CYCLE_BACKGROUND = 2;

    /**
     * Trigger type for region enter events. Region enter triggers
     * can be created with {@link Triggers#newEnterRegionTriggerBuilder()}.
     */
    public static final int REGION_ENTER = 3;

    /**
     * Trigger type for region exit events. Region exit triggers
     * can be created with {@link Triggers#newExitRegionTriggerBuilder()}.
     */
    public static final int REGION_EXIT = 4;

    /**
     * Trigger type for custom events. The count of events will be applied to
     * the triggers goal. Custom event triggers can be created
     * with {@link Triggers#newCustomEventTriggerBuilder()}.
     */
    public static final int CUSTOM_EVENT_COUNT = 5;

    /**
     * Trigger type for custom events. The event's value will be applied to the triggers
     * goal.  Custom event triggers can be created
     * with {@link Triggers#newCustomEventTriggerBuilder()}.
     */
    public static final int CUSTOM_EVENT_VALUE = 6;

    /**
     * Trigger type for screen view events. Screen events can be
     * created with {@link Triggers#newScreenTriggerBuilder()}.
     */
    public static final int SCREEN_VIEW = 7;

    /**
     * Trigger type for app initialization events. App initialization events
     * can be created with {@link Triggers#newAppInitTriggerBuilder()}.
     */
    public static final int LIFE_CYCLE_APP_INIT = 8;

    /**
     * Trigger type for active app sessions. Active app session triggers can be created with
     * {@link Triggers#newActiveSessionTriggerBuilder()}.
     */
    public static final int ACTIVE_SESSION = 9;

    /**
     * Trigger type for version. Version triggers can be create with
     * {@link Triggers#newVersionTriggerBuilder(com.urbanairship.json.ValueMatcher)}
     */
    public static final int VERSION = 10;

    /**
     * @hide
     */
    @NonNull
    public static final Creator<Trigger> CREATOR = new Creator<Trigger>() {
        @NonNull
        @Override
        public Trigger createFromParcel(@NonNull Parcel in) {
            return new Trigger(in);
        }

        @NonNull
        @Override
        public Trigger[] newArray(int size) {
            return new Trigger[size];
        }
    };

    private final int type;
    private final double goal;
    private final JsonPredicate predicate;

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public Trigger(@TriggerType int type, double goal, @Nullable JsonPredicate predicate) {
        this.type = type;
        this.goal = goal;
        this.predicate = predicate;
    }

    public Trigger(@NonNull Parcel in) {
        double goal;
        int type;
        JsonPredicate predicate = null;

        switch (in.readInt()) {
            case LIFE_CYCLE_BACKGROUND:
                type = LIFE_CYCLE_BACKGROUND;
                break;
            case LIFE_CYCLE_FOREGROUND:
                type = LIFE_CYCLE_FOREGROUND;
                break;
            case LIFE_CYCLE_APP_INIT:
                type = LIFE_CYCLE_APP_INIT;
                break;
            case REGION_ENTER:
                type = REGION_ENTER;
                break;
            case REGION_EXIT:
                type = REGION_EXIT;
                break;
            case CUSTOM_EVENT_COUNT:
                type = CUSTOM_EVENT_COUNT;
                break;
            case CUSTOM_EVENT_VALUE:
                type = CUSTOM_EVENT_VALUE;
                break;
            case SCREEN_VIEW:
                type = SCREEN_VIEW;
                break;
            case ACTIVE_SESSION:
                type = ACTIVE_SESSION;
                break;
            case VERSION:
                type = VERSION;
                break;
            default:
                throw new IllegalStateException("Invalid trigger type from parcel.");
        }

        goal = in.readDouble();

        JsonValue predicateJson = in.readParcelable(JsonValue.class.getClassLoader());
        if (predicateJson != null) {
            try {
                predicate = JsonPredicate.parse(predicateJson);
            } catch (JsonException e) {
                throw new IllegalStateException("Invalid trigger predicate from parcel.", e);
            }
        }

        this.type = type;
        this.goal = goal;
        this.predicate = predicate;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(type);
        dest.writeDouble(goal);
        dest.writeParcelable(predicate == null ? null : predicate.toJsonValue(), flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Returns the trigger type.
     *
     * @return The trigger type.
     */
    @TriggerType
    public int getType() {
        return type;
    }

    /**
     * Returns the trigger's goal. Once a trigger's goal is hit, it will cause the schedule's actions
     * to perform, then the trigger will be reset.
     *
     * @return The trigger's goal.
     */
    public double getGoal() {
        return goal;
    }

    /**
     * The trigger's predicate. The predicate is applied to the event defined by the trigger's type.
     * If the predicate matches the event, then the event's value will be applied to the trigger's goal.
     *
     * @return The trigger's predicate.
     */
    @Nullable
    public JsonPredicate getPredicate() {
        return predicate;
    }

    /**
     * {@see #fromJson(JsonValue)}
     *
     * @param json The json value.
     * @return The parsed Trigger.
     * @throws JsonException If the JSON is invalid.
     * @deprecated To be removed in SDK 12. Use {@link #fromJson(JsonValue)} instead.
     */
    @NonNull
    @Deprecated
    public static Trigger parseJson(@NonNull JsonValue json) throws JsonException {
        return fromJson(json);
    }

    /**
     * Parses a Trigger from a JsonValue.
     * <p>
     * The expected JsonValue is a map containing:
     * <pre>
     * - "goal": Required. The trigger's goal. Either the count of event occurrences or the aggregate value of custom event values ("custom_event_value").
     * - "predicate": Optional. Json predicate as defined by {@link JsonPredicate} scheme.
     * - "type": Required. The trigger type.
     * </pre>
     *
     * @param value The trigger JSON.
     * @return The parsed Trigger.
     * @throws JsonException If the JsonValue does not produce a valid Trigger.
     */
    @NonNull
    public static Trigger fromJson(@NonNull JsonValue value) throws JsonException {
        JsonMap jsonMap = value.optMap();

        @TriggerType int type;
        JsonPredicate predicate = jsonMap.containsKey(PREDICATE_KEY) ? JsonPredicate.parse(jsonMap.opt(PREDICATE_KEY)) : null;
        double goal = jsonMap.opt(GOAL_KEY).getDouble(-1);
        if (!(goal > 0)) {
            throw new JsonException("Trigger goal must be defined and greater than 0.");
        }

        String typeString = jsonMap.opt(TYPE_KEY).optString().toLowerCase(Locale.ROOT);
        switch (typeString) {
            case CUSTOM_EVENT_COUNT_NAME:
                type = CUSTOM_EVENT_COUNT;
                break;

            case CUSTOM_EVENT_VALUE_NAME:
                type = CUSTOM_EVENT_VALUE;
                break;

            case FOREGROUND_NAME:
                type = LIFE_CYCLE_FOREGROUND;
                break;

            case BACKGROUND_NAME:
                type = LIFE_CYCLE_BACKGROUND;
                break;

            case APP_INIT_NAME:
                type = LIFE_CYCLE_APP_INIT;
                break;

            case SCREEN_NAME:
                type = SCREEN_VIEW;
                break;

            case REGION_ENTER_NAME:
                type = REGION_ENTER;
                break;

            case REGION_EXIT_NAME:
                type = REGION_EXIT;
                break;

            case ACTIVE_SESSION_NAME:
                type = ACTIVE_SESSION;
                break;

            case VERSION_NAME:
                type = VERSION;
                break;

            default:
                throw new JsonException("Invalid trigger type: " + typeString);
        }

        return new Trigger(type, goal, predicate);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Trigger trigger = (Trigger) o;

        if (type != trigger.type) return false;
        if (Double.compare(trigger.goal, goal) != 0) return false;
        return predicate != null ? predicate.equals(trigger.predicate) : trigger.predicate == null;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = type;
        temp = Double.doubleToLongBits(goal);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (predicate != null ? predicate.hashCode() : 0);
        return result;
    }

}

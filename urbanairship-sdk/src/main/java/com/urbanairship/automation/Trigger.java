/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonPredicate;
import com.urbanairship.json.JsonValue;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Trigger defines a condition to execute an {@link ActionSchedule}. Use {@link Triggers} to build
 * triggers.
 */
public class Trigger implements Parcelable {

    @IntDef({ LIFE_CYCLE_FOREGROUND, LIFE_CYCLE_BACKGROUND, REGION_ENTER, REGION_EXIT, CUSTOM_EVENT_COUNT, CUSTOM_EVENT_VALUE, SCREEN_VIEW, LIFE_CYCLE_APP_INIT })
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

    public static final Creator<Trigger> CREATOR = new Creator<Trigger>() {
        @Override
        public Trigger createFromParcel(Parcel in) {
            return new Trigger(in);
        }

        @Override
        public Trigger[] newArray(int size) {
            return new Trigger[size];
        }
    };

    private final int type;
    private final double goal;
    private final JsonPredicate predicate;

    Trigger(@TriggerType int type, double goal, JsonPredicate predicate) {
        this.type = type;
        this.goal = goal;
        this.predicate = predicate;
    }

    public Trigger(Parcel in) {
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
    public void writeToParcel(Parcel dest, int flags) {
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
     * Parses a Trigger from a JsonValue.
     * <p/>
     * The expected JsonValue is a map containing:
     * <pre>
     * - "goal": Required. The trigger's goal. Either the count of event occurrences or the aggregate value of custom event values ("custom_event_value").
     * - "predicate": Optional. Json predicate as defined by {@link JsonPredicate} scheme.
     * - "type": Required. Either "custom_event_value", "custom_event_count", "foreground", "background",
     *           "region_enter", "region_exit", or "screen".
     * </pre>
     *
     * @param value The trigger JSON.
     * @return The parsed Trigger.
     * @throws JsonException If the JsonValue does not produce a valid Trigger.
     */
    public static Trigger parseJson(JsonValue value) throws JsonException {
        JsonMap jsonMap = value.optMap();

        @TriggerType int type;
        JsonPredicate predicate = jsonMap.containsKey("predicate") ? JsonPredicate.parse(jsonMap.opt("predicate")) : null;
        double goal = jsonMap.opt("goal").getDouble(-1);
        if (goal < 0) {
            throw new JsonException("Trigger goal must be defined and greater than 0.");
        }

        String typeString = jsonMap.opt("type").getString("").toLowerCase();
        switch (typeString) {
            case "custom_event_count":
                type = CUSTOM_EVENT_COUNT;
                break;

            case "custom_event_value":
                type = CUSTOM_EVENT_VALUE;
                break;

            case "foreground":
                type = LIFE_CYCLE_FOREGROUND;
                break;

            case "background":
                type = LIFE_CYCLE_BACKGROUND;
                break;

            case "app_init":
                type = LIFE_CYCLE_APP_INIT;
                break;

            case "screen":
                type = SCREEN_VIEW;
                break;

            case "region_enter":
                type = REGION_ENTER;
                break;

            case "region_exit":
                type = REGION_EXIT;
                break;

            default:
                throw new JsonException("Invalid trigger type: " + typeString);
        }

        return new Trigger(type, goal, predicate);
    }
}

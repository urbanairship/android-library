/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.support.annotation.IntDef;
import android.support.annotation.Nullable;

import com.urbanairship.json.JsonPredicate;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Trigger defines a condition to execute an {@link ActionSchedule}. Use {@link Triggers} to build
 * triggers.
 */
public class Trigger {

    @IntDef({ LIFE_CYCLE_FOREGROUND, LIFE_CYCLE_BACKGROUND, REGION_ENTER, REGION_EXIT, CUSTOM_EVENT_COUNT, CUSTOM_EVENT_VALUE, SCREEN_VIEW })
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

    private final int type;
    private final double goal;
    private final JsonPredicate predicate;

    Trigger(@TriggerType int type, double goal, JsonPredicate predicate) {
        this.type = type;
        this.goal = goal;
        this.predicate = predicate;
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

}

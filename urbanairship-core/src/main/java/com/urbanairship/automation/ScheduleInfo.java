/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.support.annotation.RestrictTo;

import com.urbanairship.json.JsonSerializable;

import java.util.List;

/**
 * Schedule info interface.
 */
public interface ScheduleInfo {

    // JSON KEYS
    String LIMIT_KEY = "limit";
    String PRIORITY_KEY = "priority";
    String GROUP_KEY = "group";
    String END_KEY = "end";
    String START_KEY = "start";
    String DELAY_KEY = "delay";
    String TRIGGERS_KEY = "triggers";
    String EDIT_GRACE_PERIOD = "edit_grace_period";
    String INTERVAL = "interval";

    /**
     * Gets the action triggers.
     *
     * @return A list of triggers.
     */
    List<Trigger> getTriggers();

    /**
     * Gets the schedule data.
     *
     * @return Schedule data.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    JsonSerializable getData();

    /**
     * Gets the schedule fulfillment limit.
     *
     * @return The fulfillment limit.
     */
    int getLimit();

    /**
     * Gets the schedule priority level.
     *
     * @return The priority level.
     */
    int getPriority();

    /**
     * Gets the schedule group.
     *
     * @return The schedule group.
     */
    String getGroup();

    /**
     * Gets the schedule start time in ms.
     *
     * @return The schedule start time in ms.
     */
    long getStart();

    /**
     * Gets the schedule end time in ms.
     *
     * @return The schedule end time in ms.
     */
    long getEnd();

    /**
     * Gets the schedule's delay.
     *
     * @return A ScheduleDelay instance.
     */
    ScheduleDelay getDelay();

    /**
     * Gets the edit grace period in ms.
     *
     * @return The edit grace period in ms.
     */
    long getEditGracePeriod();

    /**
     * Gets the schedule execution interval in ms.
     *
     * @return The interval in ms.
     */
    long getInterval();
}

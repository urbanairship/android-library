/* Copyright 2017 Urban Airship and Contributors */

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

    /**
     * Gets the action triggers.
     *
     * @return A list of triggers.
     */
    List<Trigger> getTriggers();

    /**
     * Gets the schedules data.
     *
     * @return Schedules data.
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
     * Gets the schedule start time in MS.
     *
     * @return The schedule start time in MS.
     */
    long getStart();

    /**
     * Gets the schedule end time in MS.
     *
     * @return The schedule end time in MS.
     */
    long getEnd();

    /**
     * Gets the schedule's delay.
     *
     * @return A ScheduleDelay instance.
     */
    ScheduleDelay getDelay();
}

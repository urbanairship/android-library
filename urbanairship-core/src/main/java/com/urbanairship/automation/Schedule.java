/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.automation;

/**
 * Class representing an automation action schedule - wraps {@link ScheduleInfo} with the schedule ID.
 */
public interface Schedule<T extends ScheduleInfo> {

    /**
     * Gets the schedule info.
     *
     * @return The schedule info.
     */
    T getInfo();

    /**
     * Get the schedule ID.
     *
     * @return The schedule ID.
     */
    String getId();
}

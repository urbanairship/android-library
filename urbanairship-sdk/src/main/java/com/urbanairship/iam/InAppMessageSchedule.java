/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.automation.Schedule;

/**
 * Defines an in-app message schedule.
 */
public class InAppMessageSchedule implements Schedule<InAppMessageScheduleInfo> {

    private final String id;
    private final InAppMessageScheduleInfo info;

    /**
     * Class constructor.
     *
     * @param id The schedule ID.
     * @param info The ActionScheduleInfo instance.
     */
    public InAppMessageSchedule(String id, InAppMessageScheduleInfo info) {
        this.id = id;
        this.info = info;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InAppMessageScheduleInfo getInfo() {
        return info;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getId() {
        return id;
    }
}

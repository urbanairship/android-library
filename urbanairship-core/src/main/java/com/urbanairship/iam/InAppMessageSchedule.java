/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.support.annotation.NonNull;

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
    public InAppMessageSchedule(@NonNull String id, @NonNull InAppMessageScheduleInfo info) {
        this.id = id;
        this.info = info;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public InAppMessageScheduleInfo getInfo() {
        return info;
    }


    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getId() {
        return id;
    }
}

/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import androidx.annotation.NonNull;

import com.urbanairship.json.JsonMap;

/**
 * Class representing an automation action schedule - wraps {@link ScheduleInfo} with the schedule ID.
 */
public interface Schedule<T extends ScheduleInfo> {

    /**
     * Gets the schedule's info.
     *
     * @return The schedule's info.
     */
    @NonNull
    T getInfo();

    /**
     * Get the schedule's ID.
     *
     * @return The schedule's ID.
     */
    @NonNull
    String getId();

    /**
     * Gets the schedule's metadata.
     *
     * @return The schedule's metadata.
     */
    @NonNull
    JsonMap getMetadata();

}

/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.automation;


import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;

import com.urbanairship.json.JsonSerializable;

/**
 * Available automation schedule edits.
 */
public interface ScheduleEdits {

    /**
     * Gets the schedule data.
     *
     * @return Schedule data.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Nullable
    JsonSerializable getData();

    /**
     * Gets the schedule fulfillment limit.
     *
     * @return The fulfillment limit.
     */
    @Nullable
    Integer getLimit();

    /**
     * Gets the schedule priority level.
     *
     * @return The priority level.
     */
    @Nullable
    Integer getPriority();

    /**
     * Gets the schedule start time in MS.
     *
     * @return The schedule start time in MS.
     */
    @Nullable
    Long getStart();

    /**
     * Gets the schedule end time in MS.
     *
     * @return The schedule end time in MS.
     */
    @Nullable
    Long getEnd();

    /**
     * Gets the schedule interval in MS.
     *
     * @return The schedule interval in MS.
     */
    @Nullable
    Long getInterval();

    /**
     * Gets the schedule edit grace period in MS.
     *
     * @return The schedule edit grace period in MS.
     */
    @Nullable
    Long getEditGracePeriod();
}

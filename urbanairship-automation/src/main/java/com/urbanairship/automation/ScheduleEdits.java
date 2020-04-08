/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.json.JsonMap;
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
     * Gets the schedule start time in ms.
     *
     * @return The schedule start time in ms.
     */
    @Nullable
    Long getStart();

    /**
     * Gets the schedule end time in ms.
     *
     * @return The schedule end time in ms.
     */
    @Nullable
    Long getEnd();

    /**
     * Gets the schedule interval in ms.
     *
     * @return The schedule interval in ms.
     */
    @Nullable
    Long getInterval();

    /**
     * Gets the schedule edit grace period in ms.
     *
     * @return The schedule edit grace period in ms.
     */
    @Nullable
    Long getEditGracePeriod();

    /**
     * Gets the schedule's metadata.
     *
     * @return The schedule's metadata.
     */
    @Nullable
    JsonMap getMetadata();

}

/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.json.JsonSerializable;

import java.util.List;

/**
 * Schedule info interface.
 */
public interface ScheduleInfo {

    // JSON KEYS
    @NonNull
    String LIMIT_KEY = "limit";

    @NonNull
    String PRIORITY_KEY = "priority";

    @NonNull
    String GROUP_KEY = "group";

    @NonNull
    String END_KEY = "end";

    @NonNull
    String START_KEY = "start";

    @NonNull
    String DELAY_KEY = "delay";

    @NonNull
    String TRIGGERS_KEY = "triggers";

    @NonNull
    String EDIT_GRACE_PERIOD = "edit_grace_period";

    @NonNull
    String INTERVAL = "interval";

    /**
     * Gets the action triggers.
     *
     * @return A list of triggers.
     */
    @NonNull
    List<Trigger> getTriggers();

    /**
     * Gets the schedule data.
     *
     * @return Schedule data.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
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
    @Nullable
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
    @Nullable
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

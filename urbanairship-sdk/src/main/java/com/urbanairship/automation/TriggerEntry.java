/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.automation;

import com.urbanairship.json.JsonPredicate;

/**
 * Trigger information stored in the triggers table.
 */
class TriggerEntry extends Trigger {

    private final String id;
    private final String scheduleId;
    private final double progress;
    private final long start;

    TriggerEntry(@TriggerType  int type, double limit, JsonPredicate predicate, String id, String scheduleId, double progress, long start) {
        super(type, limit, predicate);
        this.id = id;
        this.scheduleId = scheduleId;
        this.progress = progress;
        this.start = start;
    }

    /**
     * The trigger's progress to its goal.
     *
     * @return The trigger's progress.
     */
    double getProgress() {
        return progress;
    }

    /**
     * The trigger's ID.
     *
     * @return The trigger's ID.
     */
    String getId() {
        return id;
    }

    /**
     * The trigger's schedule ID.
     *
     * @return The trigger's schedule ID.
     */
    String getScheduleId() {
        return scheduleId;
    }

    /**
     * The schedules start time in milliseconds.
     *
     * @return Start time in milliseconds.
     */
    long getStart() {
        return start;
    }
}

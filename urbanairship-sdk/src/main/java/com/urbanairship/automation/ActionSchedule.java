/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.automation;

/**
 * Class representing an automation action schedule - wraps {@link ActionScheduleInfo} with schedule
 * metadata.
 */
public class ActionSchedule {

    private final String id;
    private final ActionScheduleInfo info;
    private final int count;

    /**
     * Class constructor.
     *
     * @param id The schedule ID.
     * @param info The ActionScheduleInfo instance.
     * @param count The fulfillment count.
     */
    public ActionSchedule(String id, ActionScheduleInfo info, int count) {
        this.id = id;
        this.info = info;
        this.count = count;
    }

    /**
     * Gets the ActionSchedule ID.
     *
     * @return The ActionSchedule ID.
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the ActionScheduleInfo instance.
     *
     * @return The ActionScheduleInfo instance.
     */
    public ActionScheduleInfo getInfo() {
        return info;
    }

    /**
     * Gets the schedule fulfillment count.
     *
     * @return The schedule fulfillment count.
     */
    int getCount() {
        return count;
    }

}

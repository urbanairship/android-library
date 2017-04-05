/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Class representing an automation action schedule - wraps {@link ActionScheduleInfo} with schedule
 * metadata.
 */
public class ActionSchedule implements Parcelable {

    public static final Creator<ActionSchedule> CREATOR = new Creator<ActionSchedule>() {
        @Override
        public ActionSchedule createFromParcel(Parcel in) {
            return new ActionSchedule(in);
        }

        @Override
        public ActionSchedule[] newArray(int size) {
            return new ActionSchedule[size];
        }
    };

    private final String id;
    private final ActionScheduleInfo info;
    private final int count;
    private boolean isPendingExecution;
    private long pendingExecutionDate;

    /**
     * Class constructor.
     *
     * @param id The schedule ID.
     * @param info The ActionScheduleInfo instance.
     * @param count The fulfillment count.
     * @param isPendingExecution The execution status.
     * @param pendingExecutionDate The date at which to execute the action.
     */
    public ActionSchedule(String id, ActionScheduleInfo info, int count, boolean isPendingExecution, long pendingExecutionDate) {
        this.id = id;
        this.info = info;
        this.count = count;
        this.isPendingExecution = isPendingExecution;
        this.pendingExecutionDate = pendingExecutionDate;
    }

    protected ActionSchedule(Parcel in) {
        id = in.readString();
        info = in.readParcelable(ActionScheduleInfo.class.getClassLoader());
        count = in.readInt();
        isPendingExecution = in.readInt() == 1;
        pendingExecutionDate = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeParcelable(info, flags);
        dest.writeInt(count);
        dest.writeInt(isPendingExecution ? 1 : 0);
        dest.writeLong(pendingExecutionDate);
    }

    @Override
    public int describeContents() {
        return 0;
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

    /**
     * Check whether the schedule is pending execution or not.
     *
     * @return The schedule's execution state.
     */
    boolean getIsPendingExecution() {
        return isPendingExecution;
    }


    /**
     * Get the pending execution date, in milliseconds.
     *
     * @return The pending execution date, in milliseconds.
     */
    long getPendingExecutionDate() {
        return pendingExecutionDate;
    }
}

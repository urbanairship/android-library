package com.urbanairship.automation;

import android.os.Parcel;

/**
 * Schedule delay information stored in the schedule delay table.
 */
public class ScheduleDelayEntry extends ScheduleDelay {
    private final String delayId;
    private final String scheduleId;

    // ScheduleDelayEntry should never be used as a Parceable, this is here to please the linter.
    public static final Creator<ScheduleDelay> CREATOR = new Creator<ScheduleDelay>() {
        @Override
        public ScheduleDelay createFromParcel(Parcel source) {
            return new ScheduleDelay(source);
        }

        @Override
        public ScheduleDelay[] newArray(int size) {
            return new ScheduleDelay[size];
        }
    };

    ScheduleDelayEntry(ScheduleDelay.Builder builder, String delayId, String scheduleId) {
        super(builder);
        this.delayId = delayId;
        this.scheduleId = scheduleId;
    }

    /**
     * Gets the schedule delay's ID.
     *
     * @return The schedule delay's ID.
     */
    String getDelayId() {
        return delayId;
    }

    /**
     * Gets the schedule delay's schedule ID.
     *
     * @return The delay's schedule ID.
     */
    String getScheduleId() {
        return scheduleId;
    }
}

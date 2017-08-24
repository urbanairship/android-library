package com.urbanairship.automation;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Defines an action schedule.
 */
public class ActionSchedule implements Schedule<ActionScheduleInfo>, Parcelable {

    /**
     * @hide
     */
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

    /**
     * Class constructor.
     *
     * @param id The schedule ID.
     * @param info The ActionScheduleInfo instance.
     */
    public ActionSchedule(String id, ActionScheduleInfo info) {
        this.id = id;
        this.info = info;
    }

    private ActionSchedule(Parcel in) {
        this.id = in.readString();
        this.info = in.readParcelable(ActionScheduleInfo.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeParcelable(info, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getId() {
        return id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ActionScheduleInfo getInfo() {
        return info;
    }
}

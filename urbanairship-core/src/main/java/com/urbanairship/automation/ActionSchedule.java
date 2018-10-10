package com.urbanairship.automation;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

/**
 * Defines an action schedule.
 */
public class ActionSchedule implements Schedule<ActionScheduleInfo>, Parcelable {

    /**
     * @hide
     */
    @NonNull
    public static final Creator<ActionSchedule> CREATOR = new Creator<ActionSchedule>() {
        @Override
        @NonNull
        public ActionSchedule createFromParcel(@NonNull Parcel in) {
            return new ActionSchedule(in);
        }

        @Override
        @NonNull
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
    public ActionSchedule(@NonNull String id, @NonNull ActionScheduleInfo info) {
        this.id = id;
        this.info = info;
    }

    private ActionSchedule(@NonNull Parcel in) {
        this.id = in.readString();
        this.info = in.readParcelable(ActionScheduleInfo.class.getClassLoader());
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
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
    @NonNull
    public String getId() {
        return id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public ActionScheduleInfo getInfo() {
        return info;
    }
}

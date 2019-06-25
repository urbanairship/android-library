package com.urbanairship.automation;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

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
    private final JsonMap metadata;

    /**
     * Class constructor.
     *
     * @param id The schedule ID.
     * @param metadata The metadata.
     * @param info The ActionScheduleInfo instance.
     */
    public ActionSchedule(@NonNull String id, @NonNull JsonMap metadata, @NonNull ActionScheduleInfo info) {
        this.id = id;
        this.info = info;
        this.metadata = metadata;
    }

    private ActionSchedule(@NonNull Parcel in) {
        this.id = in.readString();
        this.info = in.readParcelable(ActionScheduleInfo.class.getClassLoader());

        JsonMap parsedMetadata;
        try {
            parsedMetadata = JsonValue.parseString(in.readString()).optMap();
        } catch (JsonException e) {
            Logger.error(e, "Failed to parse metadata.");
            parsedMetadata = JsonMap.EMPTY_MAP;
        }

        this.metadata = parsedMetadata;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeParcelable(info, flags);
        dest.writeString(metadata.toString());
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
    @NonNull
    @Override
    public JsonMap getMetadata() {
        return metadata;
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

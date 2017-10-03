/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.app.Activity;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import com.urbanairship.UAirship;

/**
 * Display handler for in-app message displays.
 * <p>
 * In-app message should call {@link #requestDisplayLock(Activity)} before displaying the in-app
 * message. Typically, this should be done in an Activity's or Fragment's onStart method, or if
 * using a view, request the display lock when the attached activity visibility changes to
 * VISIBLE in onWindowVisibilityChanged.
 * <p>
 * When the in-app message is finished, call {@link #finished()}. This will finish the display of an
 * in-app message and allow it to be triggered again by one of the in-app message triggers. If the
 * hosting Activity finishes before the in-app message is able to be displayed, call {@link #continueOnNextActivity()}
 * to have the in-app message redisplay on the next activity.
 */
public class DisplayHandler implements Parcelable {

    private final String scheduleId;

    /**
     * Default constructor.
     *
     * @param scheduleId The schedule ID.
     */
    DisplayHandler(@NonNull String scheduleId) {
        this.scheduleId = scheduleId;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(scheduleId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Creator for parcelable interface.
     *
     * @hide
     */
    public static final Creator<DisplayHandler> CREATOR = new Creator<DisplayHandler>() {
        @Override
        public DisplayHandler createFromParcel(Parcel in) {
            return new DisplayHandler(in.readString());
        }

        @Override
        public DisplayHandler[] newArray(int size) {
            return new DisplayHandler[size];
        }
    };

    /**
     * Called when the in-app message needs to be displayed on the next activity. After calling this
     * method, the in-app message should immediately dismiss its view.
     */
    public void continueOnNextActivity() {
        UAirship.shared().getInAppMessagingManager().continueOnNextActivity(scheduleId);
    }

    /**
     * Called when the in-app message is finished displaying. After calling this method, the in-app
     * message should immediately dismiss its view to prevent the current activity from redisplaying
     * the in-app message if still on the back stack.
     */
    public void finished() {
        UAirship.shared().getInAppMessagingManager().messageFinished(scheduleId);
    }

    /**
     * Called to obtain the display lock. If the in-app message is being displayed in a fragment or
     * directly in an activity, it should be called in the onStart method. If the in-app message is
     * attached directly to a view it should be called in the view's onWindowVisibilityChanged when
     * the window becomes visible.
     *
     * @param activity The activity.
     * @return {@code true} if the display lock was granted or the in-app message already contained
     * the lock. Otherwise {@code false}.
     */
    public boolean requestDisplayLock(Activity activity) {
        return UAirship.shared().getInAppMessagingManager().requestDisplayLock(activity, scheduleId);
    }
}



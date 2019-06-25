/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;

/**
 * Display handler for in-app message displays.
 * <p>
 * In-app message should call {@link #isDisplayAllowed(Context)} before displaying the in-app
 * message if the in-app message is displayed in its own activity. Typically, this should be done in
 * an Activity's or Fragment's onCreate callbacks.
 * <p>
 * When the in-app message is finished, call {@link #finished(ResolutionInfo, long)}. This will finish the display of an
 * in-app message and allow it to be triggered again by one of the in-app message triggers.
 */
public class DisplayHandler implements Parcelable {

    private final String scheduleId;

    /**
     * Default constructor.
     *
     * @param scheduleId The schedule ID.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public DisplayHandler(@NonNull String scheduleId) {
        this.scheduleId = scheduleId;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
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
    @NonNull
    public static final Creator<DisplayHandler> CREATOR = new Creator<DisplayHandler>() {

        @NonNull
        @Override
        public DisplayHandler createFromParcel(@NonNull Parcel in) {
            String scheduleId = in.readString();
            return new DisplayHandler(scheduleId == null ? "" : scheduleId);
        }

        @NonNull
        @Override
        public DisplayHandler[] newArray(int size) {
            return new DisplayHandler[size];
        }
    };

    /**
     * Called when the in-app message is finished displaying. After calling this method, the in-app
     * message should immediately dismiss its view to prevent the current activity from redisplaying
     * the in-app message if still on the back stack.
     *
     * @param resolutionInfo Info on why the message has finished.
     * @param displayMilliseconds The display time in milliseconds
     */
    public void finished(@NonNull ResolutionInfo resolutionInfo, long displayMilliseconds) {
        InAppMessageManager manager = getInAppMessagingManager();
        if (manager == null) {
            Logger.error("Takeoff not called. Unable to finish display for schedule: %s", scheduleId);
            return;
        }

        manager.messageFinished(scheduleId, resolutionInfo, displayMilliseconds);
    }

    /**
     * Prevents the message from displaying again.
     */
    public void cancelFutureDisplays() {
        InAppMessageManager manager = getInAppMessagingManager();
        if (manager == null) {
            Logger.error("Takeoff not called. Unable to cancel displays for schedule: %s", scheduleId);
            return;
        }

        manager.cancelSchedule(scheduleId);
    }

    /**
     * Called to verify display is still allowed. If the in-app message is being displayed in a fragment or
     * directly in an activity, it should be called in the onStart method. If the in-app message is
     * attached directly to a view it should be called in the view's onWindowVisibilityChanged when
     * the window becomes visible.
     *
     * @return {@code true} if the message should continue displaying, otherwise {@code false}.
     */
    public boolean isDisplayAllowed(@NonNull Context context) {
        Autopilot.automaticTakeOff(context);

        InAppMessageManager manager = getInAppMessagingManager();
        if (manager == null) {
            Logger.error("Takeoff not called. Unable to request display lock.");
            return false;
        }

        return manager.isDisplayAllowed(scheduleId);
    }

    @Nullable
    private InAppMessageManager getInAppMessagingManager() {
        if (UAirship.isTakingOff() || UAirship.isFlying()) {
            return UAirship.shared().getInAppMessagingManager();
        }
        return null;
    }

}



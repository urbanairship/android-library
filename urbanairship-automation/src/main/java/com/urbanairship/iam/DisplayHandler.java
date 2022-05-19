/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.automation.InAppAutomation;
import com.urbanairship.iam.events.InAppReportingEvent;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.room.util.StringUtil;

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
    private final boolean isReportingAllowed;
    private final JsonValue campaigns;
    private final JsonValue reportingContext;

    /**
     * Default constructor.
     *
     * @param scheduleId The schedule ID.
     * @param isReportingAllowed If reporting is allowed or not.
     * @param campaigns The campaigns info.
     * @param reportingContext The reporting context.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public DisplayHandler(@NonNull String scheduleId,
                          boolean isReportingAllowed,
                          @NonNull JsonValue campaigns,
                          @NonNull JsonValue reportingContext) {
        this.scheduleId = scheduleId;
        this.isReportingAllowed = isReportingAllowed;
        this.campaigns = campaigns;
        this.reportingContext = reportingContext;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(scheduleId);
        dest.writeInt(isReportingAllowed ? 1 : 0);
        dest.writeString(campaigns.toString());
        dest.writeString(reportingContext.toString());
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
            try {
                String scheduleId = in.readString();
                boolean isReportingAllowed = in.readInt() != 0;
                JsonValue campaigns = JsonValue.parseString(in.readString());
                JsonValue reportingContext = JsonValue.parseString(in.readString());
                return new DisplayHandler(scheduleId == null ? "" : scheduleId, isReportingAllowed, campaigns, reportingContext);
            } catch (Exception e) {
                Logger.error(e, "failed to create display handler");
                return new DisplayHandler("", false, JsonValue.NULL, JsonValue.NULL);
            }
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
        InAppAutomation inAppAutomation = getInAppAutomation();
        if (inAppAutomation == null) {
            Logger.error("Takeoff not called. Unable to finish display for schedule: %s", scheduleId);
            return;
        }

        inAppAutomation.getInAppMessageManager().onResolution(scheduleId, resolutionInfo, displayMilliseconds);
        notifyFinished(resolutionInfo);

        // Cancel the schedule if a cancel button was tapped
        if (resolutionInfo.getButtonInfo() != null && ButtonInfo.BEHAVIOR_CANCEL.equals(resolutionInfo.getButtonInfo().getBehavior())) {
            inAppAutomation.cancelSchedule(scheduleId);
        }
    }

    /**
     * Called when the in-app message is finished displaying.
     *
     * This method differs from {@link #finished(ResolutionInfo, long)} by not inspecting the resolution info to cancel the IAA.
     *
     * @param resolutionInfo Info on why the message has finished.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void notifyFinished(@NonNull ResolutionInfo resolutionInfo) {
        InAppAutomation inAppAutomation = getInAppAutomation();
        if (inAppAutomation == null) {
            Logger.error("Takeoff not called. Unable to finish display for schedule: %s", scheduleId);
            return;
        }

        inAppAutomation.getInAppMessageManager().onDisplayFinished(scheduleId, resolutionInfo);
    }

    /**
     * Adds an event.
     *
     * @param event The event.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void addEvent(InAppReportingEvent event) {
        if (isReportingAllowed) {
            Analytics analytics = getAnalytics();
            if (analytics == null) {
                Logger.error("Takeoff not called. Unable to add event for schedule: %s", scheduleId);
                return;
            }

            event.setCampaigns(campaigns)
                 .setReportingContext(reportingContext)
                 .record(analytics);
        }
    }

    /**
     * Prevents the message from displaying again.
     */
    public void cancelFutureDisplays() {
        InAppAutomation inAppAutomation = getInAppAutomation();
        if (inAppAutomation == null) {
            Logger.error("Takeoff not called. Unable to cancel displays for schedule: %s", scheduleId);
            return;
        }

        inAppAutomation.cancelSchedule(scheduleId);
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

        InAppAutomation inAppAutomation = getInAppAutomation();
        if (inAppAutomation == null) {
            Logger.error("Takeoff not called. Unable to request display lock.");
            return false;
        }

        return inAppAutomation.getInAppMessageManager().isDisplayAllowed(scheduleId);
    }

    @Nullable
    private InAppAutomation getInAppAutomation() {
        if (UAirship.isTakingOff() || UAirship.isFlying()) {
            return InAppAutomation.shared();
        }
        return null;
    }

    @Nullable
    private Analytics getAnalytics() {
        if (UAirship.isTakingOff() || UAirship.isFlying()) {
            return UAirship.shared().getAnalytics();
        }
        return null;
    }

    /**
     * Gets the schedule ID.
     *
     * @return The schedule ID.
     */
    public String getScheduleId() {
        return scheduleId;
    }

}

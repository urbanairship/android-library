package com.urbanairship.automation;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

public class ScheduleDelay implements Parcelable {

    @IntDef({APP_STATE_ANY, APP_STATE_FOREGROUND, APP_STATE_BACKGROUND})
    @Retention(RetentionPolicy.SOURCE)
    public @interface AppState {};

    /**
     * Type representing either the foreground or background app state.
     */
    public static final int APP_STATE_ANY = 1;

    /**
     * Type representing the foreground app state.
     */
    public static final int APP_STATE_FOREGROUND = 2;

    /**
     * Type representing the background app state.
     */
    public static final int APP_STATE_BACKGROUND = 3;

    public static final Creator<ScheduleDelay> CREATOR = new Creator<ScheduleDelay>() {
        @Override
        public ScheduleDelay createFromParcel(Parcel in) {
            return new ScheduleDelay(in);
        }

        @Override
        public ScheduleDelay[] newArray(int size) {
            return new ScheduleDelay[size];
        }
    };

    private final long seconds;
    private final String screen;
    private final int appState;
    private final String regionId;
    private final List<Trigger> cancellationTriggers;

    ScheduleDelay(Builder builder) {
        this.seconds = builder.seconds;
        this.screen = builder.screen;
        this.appState = builder.appState;
        this.regionId = builder.regionId;
        this.cancellationTriggers = builder.cancellationTriggers;
    }

    protected ScheduleDelay(Parcel in) {
        this.seconds = in.readLong();
        this.screen = in.readString();

        int appState;
        switch (in.readInt()) {
            case APP_STATE_ANY:
                appState = APP_STATE_ANY;
                break;
            case APP_STATE_FOREGROUND:
                appState = APP_STATE_FOREGROUND;
                break;
            case APP_STATE_BACKGROUND:
                appState = APP_STATE_BACKGROUND;
                break;
            default:
                throw new IllegalStateException("Invalid app state from parcel.");
        }
        this.appState = appState;
        this.regionId = in.readString();
        this.cancellationTriggers = in.createTypedArrayList(Trigger.CREATOR);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(seconds);
        dest.writeString(screen);
        dest.writeInt(appState);
        dest.writeString(regionId);
        dest.writeTypedList(cancellationTriggers);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Creates a new Builder instance.
     *
     * @return The Builder instance.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Gets the delay seconds.
     *
     * @return The delay in seconds.
     */
    public long getSeconds() {
        return seconds;
    }

    /**
     * Get the execution screen.
     *
     * @return The execution screen.
     */
    public String getScreen() {
        return screen;
    }

    /**
     * Get the execution app state.
     *
     * @return The execution app state.
     */
    @AppState
    public int getAppState() {
        return appState;
    }

    /**
     * Get the execution region ID.
     *
     * @return The execution region ID.
     */
    public String getRegionId() {
        return regionId;
    }

    /**
     * Get the list of cancellation triggers.
     *
     * @return The list of cancellation triggers.
     */
    public List<Trigger> getCancellationTriggers() {
        return cancellationTriggers;
    }

    /**
     * Parses a ScheduleDelay from JSON.
     * </p>
     * <pre>
     * - "seconds": Required. The minimum time in seconds that is needed to pass before running the actions.
     * - "screen": Optional. Specifies the name of an app screen that the user must currently be viewing
     * before the schedule's actions are able to be executed.
     * - "app_state": Required. Specifies the app state that is required before the schedule's actions are able
     * to execute. Either "foreground" or "background".
     * - "region": Optional. Specifies the ID of a region that the device must currently be in before the
     * schedule's actions are able to be executed.
     * - "cancellation_triggers": Optional. An array of triggers. Each cancels the pending execution of
     * the actions.
     * </pre>
     *
     * @param value The schedule delay.
     * @return The parsed ScheduleDelay.
     * @throws JsonException If the json does not produce a valid ScheduleDelay.
     */
    public static ScheduleDelay parseJson(@NonNull JsonValue value) throws JsonException {
        JsonMap jsonMap = value.optMap();

        Builder builder = ScheduleDelay.newBuilder()
                .setSeconds(jsonMap.opt("seconds").getLong(0));

        @AppState int appState;
        String appStateString = jsonMap.opt("app_state").getString("").toLowerCase();
        switch (appStateString) {
            case "any":
                appState = APP_STATE_ANY;
                break;
            case "foreground":
                appState = APP_STATE_FOREGROUND;
                break;
            case "background":
                appState = APP_STATE_BACKGROUND;
                break;
            default:
                throw new JsonException("Invalid app state: " + appStateString);
        }
        builder.setAppState(appState);

        if (jsonMap.containsKey("screen")) {
            builder.setScreen(jsonMap.opt("screen").getString(""));
        }

        if (jsonMap.containsKey("region_id")) {
            builder.setRegionId(jsonMap.opt("region_id").getString(""));
        }

        for (JsonValue triggerJson : jsonMap.opt("triggers").optList()) {
            builder.addCancellationTrigger(Trigger.parseJson(triggerJson));
        }

        try {
            return builder.build();
        } catch (IllegalArgumentException e) {
            throw new JsonException("Invalid schedule delay info", e);
        }
    }

    /**
     * Builder class.
     */
    public static class Builder {
        private long seconds;
        private String screen = null;
        private int appState = APP_STATE_ANY;
        private String regionId = null;
        private List<Trigger> cancellationTriggers = new ArrayList<>();

        /**
         * Sets the delay in seconds.
         *
         * @param seconds The delay in seconds.
         * @return The Builder instance.
         */
        public Builder setSeconds(long seconds) {
            this.seconds = seconds;
            return this;
        }

        /**
         * Sets the app screen.
         *
         * @param screen The app screen.
         * @return The Builder instance.
         */
        public Builder setScreen(String screen) {
            this.screen = screen;
            return this;
        }

        /**
         * Sets the app state.
         *
         * @param appState The app state.
         * @return The Builder instance.
         */
        public Builder setAppState(@AppState int appState) {
            this.appState = appState;
            return this;
        }

        /**
         * Sets the region ID.
         *
         * @param regionId The region ID.
         * @return The Builder instance.
         */
        public Builder setRegionId(String regionId) {
            this.regionId = regionId;
            return this;
        }

        /**
         * Adds a cancellation trigger.
         *
         * @param cancellationTrigger The cancellation trigger.
         * @return The Builder instance.
         */
        public Builder addCancellationTrigger(Trigger cancellationTrigger) {
            this.cancellationTriggers.add(cancellationTrigger);
            return this;
        }

        /**
         * Builds the ScheduleDelay instance.
         *
         * @return The ScheduleDelay instance.
         */
        public ScheduleDelay build() {
            if (cancellationTriggers.size() > ActionScheduleInfo.TRIGGER_LIMIT) {
                throw new IllegalArgumentException("No more than " + ActionScheduleInfo.TRIGGER_LIMIT + " cancellation triggers allowed.");
            }

            return new ScheduleDelay(this);
        }
    }
}

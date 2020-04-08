package com.urbanairship.automation;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Defines conditions that might delay the execution of a schedule.
 */
public class ScheduleDelay implements Parcelable {

    private static final String SECONDS_KEY = "seconds";

    // JSON Keys
    private static final String APP_STATE_KEY = "app_state";

    private static final String APP_STATE_ANY_NAME = "any";

    private static final String APP_STATE_FOREGROUND_NAME = "foreground";

    private static final String APP_STATE_BACKGROUND_NAME = "background";

    private static final String SCREEN_KEY = "screen";

    private static final String REGION_ID_KEY = "region_id";

    private static final String CANCELLATION_TRIGGERS_KEY = "cancellation_triggers";

    @IntDef({ APP_STATE_ANY, APP_STATE_FOREGROUND, APP_STATE_BACKGROUND })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AppState {}

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

    /**
     * @hide
     */
    @NonNull
    public static final Creator<ScheduleDelay> CREATOR = new Creator<ScheduleDelay>() {
        @Override
        public ScheduleDelay createFromParcel(@NonNull Parcel in) {
            return new ScheduleDelay(in);
        }

        @Override
        public ScheduleDelay[] newArray(int size) {
            return new ScheduleDelay[size];
        }
    };

    private final long seconds;
    private final List<String> screens;
    private final int appState;
    private final String regionId;
    private final List<Trigger> cancellationTriggers;

    ScheduleDelay(@NonNull Builder builder) {
        this.seconds = builder.seconds;
        this.screens = builder.screens;
        this.appState = builder.appState;
        this.regionId = builder.regionId;
        this.cancellationTriggers = builder.cancellationTriggers;
    }

    protected ScheduleDelay(@NonNull Parcel in) {
        this.seconds = in.readLong();
        this.screens = new ArrayList<>();
        in.readList(this.screens, String.class.getClassLoader());

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
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeLong(seconds);
        dest.writeList(screens);
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
    @NonNull
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
     * Get the execution screens
     *
     * @return The execution screens.
     */
    @Nullable
    public List<String> getScreens() {
        return screens;
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
    @Nullable
    public String getRegionId() {
        return regionId;
    }

    /**
     * Get the list of cancellation triggers.
     *
     * @return The list of cancellation triggers.
     */
    @NonNull
    public List<Trigger> getCancellationTriggers() {
        return cancellationTriggers;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ScheduleDelay that = (ScheduleDelay) o;

        if (seconds != that.seconds) return false;
        if (appState != that.appState) return false;
        if (screens != null ? !screens.equals(that.screens) : that.screens != null) return false;
        if (regionId != null ? !regionId.equals(that.regionId) : that.regionId != null)
            return false;
        return cancellationTriggers.equals(that.cancellationTriggers);
    }

    @Override
    public int hashCode() {
        int result = (int) (seconds ^ (seconds >>> 32));
        result = 31 * result + (screens != null ? screens.hashCode() : 0);
        result = 31 * result + appState;
        result = 31 * result + (regionId != null ? regionId.hashCode() : 0);
        result = 31 * result + cancellationTriggers.hashCode();
        return result;
    }

    /**
     * {@see #fromJson(JsonValue)}
     *
     * @param json The json value.
     * @return The parsed ScheduleDelay.
     * @throws JsonException If the JSON is invalid.
     * @deprecated To be removed in SDK 12. Use {@link #fromJson(JsonValue)} instead.
     */
    @NonNull
    @Deprecated
    public static ScheduleDelay parseJson(@NonNull JsonValue json) throws JsonException {
        return fromJson(json);
    }

    /**
     * Parses a ScheduleDelay from JSON.
     * <p>
     * <pre>
     * - "seconds": Optional. The minimum time in seconds that is needed to pass before running the actions.
     * - "screen": Optional string or array of strings. Specifies the name of an app screen that the user must
     * currently be viewing before the schedule's actions are able to be executed.
     * - "app_state": Optional. Specifies the app state that is required before the schedule's actions are able
     * to execute. Either "foreground" or "background". Invalid app states will throw a JsonException.
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
    @NonNull
    public static ScheduleDelay fromJson(@NonNull JsonValue value) throws JsonException {
        JsonMap jsonMap = value.optMap();

        Builder builder = ScheduleDelay.newBuilder()
                                       .setSeconds(jsonMap.opt(SECONDS_KEY).getLong(0));

        @AppState int appState;
        String appStateString = jsonMap.opt(APP_STATE_KEY).getString(APP_STATE_ANY_NAME).toLowerCase(Locale.ROOT);
        switch (appStateString) {
            case APP_STATE_ANY_NAME:
                appState = APP_STATE_ANY;
                break;
            case APP_STATE_FOREGROUND_NAME:
                appState = APP_STATE_FOREGROUND;
                break;
            case APP_STATE_BACKGROUND_NAME:
                appState = APP_STATE_BACKGROUND;
                break;
            default:
                throw new JsonException("Invalid app state: " + appStateString);
        }
        builder.setAppState(appState);

        if (jsonMap.containsKey(SCREEN_KEY)) {
            JsonValue screenValue = jsonMap.opt(SCREEN_KEY);
            if (screenValue.isString()) {
                builder.setScreen(screenValue.optString());
            } else {
                builder.setScreens(screenValue.optList());
            }
        }

        if (jsonMap.containsKey(REGION_ID_KEY)) {
            builder.setRegionId(jsonMap.opt(REGION_ID_KEY).optString());
        }

        for (JsonValue triggerJson : jsonMap.opt(CANCELLATION_TRIGGERS_KEY).optList()) {
            builder.addCancellationTrigger(Trigger.fromJson(triggerJson));
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
        private List<String> screens;
        private int appState = APP_STATE_ANY;
        private String regionId = null;
        private final List<Trigger> cancellationTriggers = new ArrayList<>();

        /**
         * Sets the delay in seconds.
         *
         * @param seconds The delay in seconds.
         * @return The Builder instance.
         */
        @NonNull
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
        @NonNull
        public Builder setScreen(@NonNull String screen) {
            this.screens = Collections.singletonList(screen);
            return this;
        }

        /**
         * Sets the app screens.
         *
         * @param screens The app screens.
         * @return The Builder instance.
         */
        @NonNull
        public Builder setScreens(@NonNull List<String> screens) {
            this.screens = screens;
            return this;
        }

        /**
         * Sets the app screens.
         *
         * @param screens The app screens.
         * @return The Builder instance.
         */
        @NonNull
        public Builder setScreens(@NonNull JsonList screens) {
            this.screens = new ArrayList<>();
            for (JsonValue value : screens) {
                if (value.getString() != null) {
                    this.screens.add(value.getString());
                }
            }
            return this;
        }

        /**
         * Sets the app state.
         *
         * @param appState The app state.
         * @return The Builder instance.
         */
        @NonNull
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
        @NonNull
        public Builder setRegionId(@Nullable String regionId) {
            this.regionId = regionId;
            return this;
        }

        /**
         * Adds a cancellation trigger.
         *
         * @param cancellationTrigger The cancellation trigger.
         * @return The Builder instance.
         */
        @NonNull
        public Builder addCancellationTrigger(@NonNull Trigger cancellationTrigger) {
            this.cancellationTriggers.add(cancellationTrigger);
            return this;
        }

        /**
         * Builds the ScheduleDelay instance.
         *
         * @return The ScheduleDelay instance.
         */
        @NonNull
        public ScheduleDelay build() {
            if (cancellationTriggers.size() > ActionScheduleInfo.TRIGGER_LIMIT) {
                throw new IllegalArgumentException("No more than " + ActionScheduleInfo.TRIGGER_LIMIT + " cancellation triggers allowed.");
            }

            return new ScheduleDelay(this);
        }

    }

}

/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonMap;

/**
 * A screen tracking event allows users to track an activity by associating a
 * screen identifier within an activity's onStart callback.
 */
class ScreenTrackingEvent extends Event {


    /**
     * The screen tracking type key.
     */
    static final String TYPE = "screen_tracking";

    /**
     * The maximum screen tracking event identifier length.
     */
    static final int SCREEN_TRACKING_EVENT_MAX_CHARACTERS = 255;

    /**
     * The screen key.
     */
    static final String SCREEN_KEY = "screen";

    /**
     * The previous screen key.
     */
    static final String PREVIOUS_SCREEN_KEY = "previous_screen";

    /**
     * The start time key.
     */
    static final String START_TIME_KEY = "entered_time";

    /**
     * The stop time key.
     */
    static final String STOP_TIME_KEY = "exited_time";

    /**
     * The duration key.
     */
    static final String DURATION_KEY = "duration";

    private final String screen;
    private final long startTime;
    private final long stopTime;

    private final String previousScreen;

    /**
     * Constructor for creating a screen tracking event.
     *
     * @param screen The ID of the screen.
     * @param previousScreen The optional ID of the previously tracked screen.
     * @param startTime The screen tracking start time in milliseconds.
     * @param stopTime The screen tracking stop time in milliseconds.
     */
    ScreenTrackingEvent(@NonNull String screen, @Nullable String previousScreen, long startTime, long stopTime) {
        this.screen = screen;
        this.startTime = startTime;
        this.stopTime = stopTime;
        this.previousScreen = previousScreen;
    }

    @Override
    public boolean isValid() {
        if (screen.length() > SCREEN_TRACKING_EVENT_MAX_CHARACTERS || screen.length() <= 0) {
            Logger.error("Screen identifier string must be between 1 and 255 characters long.");
            return false;
        }

        if (startTime > stopTime) {
            Logger.error("Screen tracking duration must be positive or zero.");
            return false;
        }

        return true;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    protected final JsonMap getEventData() {
        return JsonMap.newBuilder()
                .put(SCREEN_KEY, screen)
                .put(START_TIME_KEY, Event.millisecondsToSecondsString(startTime))
                .put(STOP_TIME_KEY, Event.millisecondsToSecondsString(stopTime))
                .put(DURATION_KEY, Event.millisecondsToSecondsString(stopTime - startTime))
                .put(PREVIOUS_SCREEN_KEY, previousScreen)
                .build();
    }
}

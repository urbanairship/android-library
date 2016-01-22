/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.analytics;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.Logger;

import org.json.JSONException;
import org.json.JSONObject;

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
    protected final JSONObject getEventData() {
        JSONObject data = new JSONObject();

        try {
            data.put(SCREEN_KEY, screen);
            data.put(START_TIME_KEY, Event.millisecondsToSecondsString(startTime));
            data.put(STOP_TIME_KEY, Event.millisecondsToSecondsString(stopTime));
            data.put(DURATION_KEY, Event.millisecondsToSecondsString(stopTime - startTime));
            data.putOpt(PREVIOUS_SCREEN_KEY, previousScreen);
        } catch (JSONException e) {
            Logger.error("ScreenTrackingEvent - Error constructing JSON data.", e);
        }

        return data;
    }
}

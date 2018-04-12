package com.urbanairship.iam;
/* Copyright 2018 Urban Airship and Contributors */


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Info used to generate an event when a message is finished.
 */
public final class ResolutionInfo {

    @StringDef({ ResolutionEvent.RESOLUTION_BUTTON_CLICK, ResolutionEvent.RESOLUTION_MESSAGE_CLICK, ResolutionEvent.RESOLUTION_USER_DISMISSED, ResolutionEvent.RESOLUTION_TIMED_OUT })
    @Retention(RetentionPolicy.SOURCE)
    @interface Type {}

    @NonNull
    final String type;

    @Nullable
    final ButtonInfo buttonInfo;

    final long displayMilliseconds;


    /**
     * Default constructor.
     *
     * @param type The resolution type.
     * @param displayMilliseconds The display time in milliseconds.
     */
    private ResolutionInfo(@Type @NonNull String type, long displayMilliseconds) {
        this.type = type;
        this.displayMilliseconds = displayMilliseconds > 0 ? displayMilliseconds : 0;
        this.buttonInfo = null;
    }

    /**
     * Default constructor.
     *
     * @param type The resolution type.
     * @param displayMilliseconds The display time in milliseconds.
     * @param buttonInfo The optional button info.
     */
    private ResolutionInfo(@Type @NonNull String type, long displayMilliseconds, @NonNull ButtonInfo buttonInfo) {
        this.type = type;
        this.displayMilliseconds = displayMilliseconds > 0 ? displayMilliseconds : 0;
        this.buttonInfo = buttonInfo;
    }

    /**
     * Factory method to create a resolution info for a button press.
     *
     * @param buttonInfo The button info.
     * @param displayMilliseconds How long in milliseconds the in-app message was displayed.
     * @return The resolution info.
     */
    public static ResolutionInfo buttonPressed(ButtonInfo buttonInfo, long displayMilliseconds) {
        return new ResolutionInfo(ResolutionEvent.RESOLUTION_BUTTON_CLICK, displayMilliseconds, buttonInfo);
    }

    /**
     * Factory method to create a resolution info for when a clickable in-app message was clicked.
     *
     * @param displayMilliseconds How long in milliseconds the in-app message was displayed.
     * @return The resolution info.
     */
    public static ResolutionInfo messageClicked(long displayMilliseconds) {
        return new ResolutionInfo(ResolutionEvent.RESOLUTION_MESSAGE_CLICK, displayMilliseconds);
    }

    /**
     * Factory method to create a resolution info for when the user dismissed the in-app message.
     *
     * @param displayMilliseconds How long in milliseconds the in-app message was displayed.
     * @return The resolution info.
     */
    public static ResolutionInfo dismissed(long displayMilliseconds) {
        return new ResolutionInfo(ResolutionEvent.RESOLUTION_USER_DISMISSED, displayMilliseconds);
    }

    /**
     * Factory method to create a resolution info for when the in-app message times out and auto dismisses.
     *
     * @param displayMilliseconds How long in milliseconds the in-app message was displayed.
     * @return The resolution info.
     */
    public static ResolutionInfo timedOut(long displayMilliseconds) {
        return new ResolutionInfo(ResolutionEvent.RESOLUTION_TIMED_OUT, displayMilliseconds);
    }
}

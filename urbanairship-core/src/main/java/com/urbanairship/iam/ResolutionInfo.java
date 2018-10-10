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

    @StringDef({ RESOLUTION_BUTTON_CLICK, RESOLUTION_MESSAGE_CLICK, RESOLUTION_USER_DISMISSED, RESOLUTION_TIMED_OUT })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {}

    /**
     * Button click resolution.
     */
    @NonNull
    public static final String RESOLUTION_BUTTON_CLICK = "button_click";

    /**
     * Message click resolution
     */
    @NonNull
    public static final String RESOLUTION_MESSAGE_CLICK = "message_click";

    /**
     * User dismissed resolution.
     */
    @NonNull
    public static final String RESOLUTION_USER_DISMISSED = "user_dismissed";

    /**
     * Timed out resolution.
     */
    @NonNull
    public static final String RESOLUTION_TIMED_OUT = "timed_out";

    @NonNull
    private final String type;
    @Nullable
    private final ButtonInfo buttonInfo;
    private final long displayMilliseconds;

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
    @NonNull
    public static ResolutionInfo buttonPressed(@NonNull ButtonInfo buttonInfo, long displayMilliseconds) {
        return new ResolutionInfo(RESOLUTION_BUTTON_CLICK, displayMilliseconds, buttonInfo);
    }

    /**
     * Factory method to create a resolution info for when a clickable in-app message was clicked.
     *
     * @param displayMilliseconds How long in milliseconds the in-app message was displayed.
     * @return The resolution info.
     */
    @NonNull
    public static ResolutionInfo messageClicked(long displayMilliseconds) {
        return new ResolutionInfo(RESOLUTION_MESSAGE_CLICK, displayMilliseconds);
    }

    /**
     * Factory method to create a resolution info for when the user dismissed the in-app message.
     *
     * @param displayMilliseconds How long in milliseconds the in-app message was displayed.
     * @return The resolution info.
     */
    @NonNull
    public static ResolutionInfo dismissed(long displayMilliseconds) {
        return new ResolutionInfo(RESOLUTION_USER_DISMISSED, displayMilliseconds);
    }

    /**
     * Factory method to create a resolution info for when the in-app message times out and auto dismisses.
     *
     * @param displayMilliseconds How long in milliseconds the in-app message was displayed.
     * @return The resolution info.
     */
    @NonNull
    public static ResolutionInfo timedOut(long displayMilliseconds) {
        return new ResolutionInfo(RESOLUTION_TIMED_OUT, displayMilliseconds);
    }

    /**
     * The resolution type.
     *
     * @return The resolution type.
     */
    @NonNull
    @Type
    public String getType() {
        return type;
    }

    /**
     * The button info. Only available if the type is {@link #RESOLUTION_BUTTON_CLICK}.
     *
     * @return The button info.
     */
    @Nullable
    public ButtonInfo getButtonInfo() {
        return buttonInfo;
    }

    /**
     * The elapsed time the message was displayed in milliseconds.
     *
     * @return The display time in milliseconds.
     */
    public long getDisplayMilliseconds() {
        return displayMilliseconds;
    }
}

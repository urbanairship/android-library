/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.support.annotation.NonNull;

import com.urbanairship.UAirship;
import java.util.concurrent.TimeUnit;

/**
 * Action to temporarily enable channel capture.
 * <p/>
 * Accepted situations: SITUATION_MANUAL_INVOCATION and SITUATION_PUSH_RECEIVED.
 * <p/>
 * Accepted argument value - An integer specifying the number of seconds to enable channel capture.
 * Negative values will disable channel capture.
 * <p/>
 * Result value: null.
 * <p/>
 * Default Registration Names: {@link #DEFAULT_REGISTRY_NAME}
 */
public class ChannelCaptureAction extends Action {

    /**
     * Default registry name
     */
    public static final String DEFAULT_REGISTRY_NAME = "channel_capture_action";

    /**
     * Default registry short name
     */
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^cc";

    @Override
    public boolean acceptsArguments(@NonNull ActionArguments arguments) {
        switch (arguments.getSituation()) {
            case Action.SITUATION_MANUAL_INVOCATION:
            case Action.SITUATION_PUSH_RECEIVED:
                if (arguments.getValue().toJsonValue().isInteger()) {
                    return true;
                }
                return false;

            case Action.SITUATION_WEB_VIEW_INVOCATION:
            case Action.SITUATION_AUTOMATION:
            case Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON:
            case Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON:
            case Action.SITUATION_PUSH_OPENED:
            default:
                return false;
        }
    }

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {
        long enableTime = arguments.getValue().getLong(0);
        if (enableTime > 0) {
            UAirship.shared().getChannelCapture().enable(enableTime, TimeUnit.SECONDS);
        } else {
            UAirship.shared().getChannelCapture().disable();
        }
        return ActionResult.newEmptyResult();
    }
}

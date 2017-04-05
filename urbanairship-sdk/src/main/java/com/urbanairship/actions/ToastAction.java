/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.support.annotation.NonNull;
import android.widget.Toast;

import com.urbanairship.UAirship;

/**
 * An action that displays text in a toast.
 * <p/>
 * Accepted situations: SITUATION_PUSH_OPENED, SITUATION_WEB_VIEW_INVOCATION,
 * SITUATION_MANUAL_INVOCATION, SITUATION_AUTOMATION, SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON,
 * and SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 * <p/>
 * Accepted argument value - A string with the toast text or a map with:
 * <ul>
 * <li>{@link #LENGTH_KEY}: int either {@link Toast#LENGTH_LONG} or {@link Toast#LENGTH_SHORT}, Optional</li>
 * <li>{@link #TEXT_KEY}: String, Required</li>
 * </ul>
 * <p/>
 * Result value: The arguments value.
 * <p/>
 * Default Registration Names: toast_action
 */
public class ToastAction extends Action {

    /**
     * Default registry name
     */
    public static final String DEFAULT_REGISTRY_NAME = "toast_action";

    /**
     * Key to define the Toast's text when providing the action's value as a map.
     */
    public static final String TEXT_KEY = "text";

    /**
     * Key to define the Toast's length when providing the action's value as a map.
     */
    public static final String LENGTH_KEY = "length";

    @Override
    public boolean acceptsArguments(@NonNull ActionArguments arguments) {
        switch (arguments.getSituation()) {
            case SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON:
            case SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON:
            case SITUATION_PUSH_OPENED:
            case SITUATION_MANUAL_INVOCATION:
            case SITUATION_WEB_VIEW_INVOCATION:
            case SITUATION_AUTOMATION:
                if (arguments.getValue().getMap() != null) {
                    return arguments.getValue().getMap().get(TEXT_KEY).isString();
                }

                return arguments.getValue().getString() != null;

            case SITUATION_PUSH_RECEIVED:
            default:
                return false;
        }
    }

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {
        final String text;
        final int length;

        if (arguments.getValue().getMap() != null) {
            length = arguments.getValue().getMap().opt(LENGTH_KEY).getInt(Toast.LENGTH_SHORT);
            text = arguments.getValue().getMap().opt(TEXT_KEY).getString();
        } else {
            text = arguments.getValue().getString();
            length = Toast.LENGTH_SHORT;
        }

        if (length == Toast.LENGTH_LONG) {
            Toast.makeText(UAirship.getApplicationContext(), text, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(UAirship.getApplicationContext(), text, Toast.LENGTH_SHORT).show();
        }

        return ActionResult.newResult(arguments.getValue());
    }

    @Override
    public boolean shouldRunOnMainThread() {
        return true;
    }
}

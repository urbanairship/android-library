/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.support.annotation.NonNull;

import com.urbanairship.UAirship;
import com.urbanairship.json.JsonValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Action to cancel automation schedules.
 * <p/>
 * Accepted situations: SITUATION_MANUAL_INVOCATION, SITUATION_WEB_VIEW_INVOCATION,
 * SITUATION_AUTOMATION, and SITUATION_PUSH_RECEIVED.
 * <p/>
 * Accepted argument value - Either {@link #ALL} or a map with:
 * <ul>
 * <li>{@link #GROUPS}: List of schedule groups or a single group. Optional.</li>
 * <li>{@link #IDS}: List of schedule IDs or a single schedule Id. Optional.</li>
 * </ul>
 * <p/>
 * Result value: null.
 * <p/>
 * Default Registration Names: {@link #DEFAULT_REGISTRY_NAME}, {@link #DEFAULT_REGISTRY_SHORT_NAME}
 * <p/>
 * Default Registration Predicate: none
 */
public class CancelSchedulesAction extends Action {

    /**
     * Default registry name
     */
    public static final String DEFAULT_REGISTRY_NAME = "cancel_scheduled_actions";

    /**
     * Default registry short name
     */
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^csa";

    /**
     * Used as the key in the action's value map to specify schedule groups to cancel.
     */
    public static final String GROUPS = "groups";

    /**
     * Used as the key in the action's value map to specify schedule IDs to cancel.
     */
    public static final String IDS = "ids";

    /**
     * Used as the action's value to cancel all schedules.
     */
    public static final String ALL = "all";

    @Override
    public boolean acceptsArguments(@NonNull ActionArguments arguments) {
        switch (arguments.getSituation()) {
            case Action.SITUATION_MANUAL_INVOCATION:
            case Action.SITUATION_WEB_VIEW_INVOCATION:
            case Action.SITUATION_PUSH_RECEIVED:
            case Action.SITUATION_AUTOMATION:
                if (arguments.getValue().toJsonValue().isString()) {
                    return ALL.equalsIgnoreCase(arguments.getValue().getString());
                }

                return arguments.getValue().toJsonValue().isJsonMap();

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

        JsonValue jsonValue = arguments.getValue().toJsonValue();

        // All
        if (jsonValue.isString() && ALL.equalsIgnoreCase(jsonValue.getString())) {
            UAirship.shared().getAutomation().cancelAll();
            return ActionResult.newEmptyResult();
        }

        // Groups
        JsonValue groupsJson = jsonValue.optMap().opt(GROUPS);
        if (groupsJson.isString()) {
            UAirship.shared().getAutomation().cancelGroup(groupsJson.getString());
        } else if (groupsJson.isJsonList()){
            for (JsonValue value : groupsJson.getList()) {
                if (value.isString()) {
                    UAirship.shared().getAutomation().cancelGroup(value.getString());
                }
            }
        }

        // IDs
        JsonValue idsJson = jsonValue.optMap().opt(IDS);
        if (idsJson.isString()) {
            UAirship.shared().getAutomation().cancel(idsJson.getString());
        } else if (idsJson.isJsonList()) {
            List<String> ids = new ArrayList<>();
            for (JsonValue value : idsJson.getList()) {
                if (value.isString()) {
                    ids.add(value.getString());
                }
            }

            UAirship.shared().getAutomation().cancel(ids);
        }

        return ActionResult.newEmptyResult();
    }
}

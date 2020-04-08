/* Copyright Airship and Contributors */

package com.urbanairship.automation.actions;

import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.automation.ActionAutomation;
import com.urbanairship.automation.ActionSchedule;
import com.urbanairship.automation.ActionScheduleInfo;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.AirshipComponentUtils;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

/**
 * Action to schedule {@link ActionScheduleInfo}.
 * <p>
 * Accepted situations: SITUATION_MANUAL_INVOCATION, SITUATION_WEB_VIEW_INVOCATION,
 * SITUATION_AUTOMATION, and SITUATION_PUSH_RECEIVED.
 * <p>
 * Accepted argument value - JsonValue defined by {@link ActionScheduleInfo#fromJson(JsonValue)}.
 * <p>
 * Result value: Schedule ID.
 * <p>
 * Default Registration Names: {@link #DEFAULT_REGISTRY_NAME}, {@link #DEFAULT_REGISTRY_SHORT_NAME}
 * <p>
 * Default Registration Predicate: none
 */
public class ScheduleAction extends Action {

    /**
     * Default registry name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_NAME = "schedule_actions";

    /**
     * Default registry short name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^sa";

    private final Callable<ActionAutomation> actionAutomationCallable;

    /**
     * Default constructor.
     */
    public ScheduleAction() {
        this(AirshipComponentUtils.callableForComponent(ActionAutomation.class));
    }

    /**
     * @hide
     */
    @VisibleForTesting
    ScheduleAction(@NonNull Callable<ActionAutomation> actionAutomationCallable) {
        this.actionAutomationCallable = actionAutomationCallable;
    }

    @Override
    public boolean acceptsArguments(@NonNull ActionArguments arguments) {
        switch (arguments.getSituation()) {
            case Action.SITUATION_MANUAL_INVOCATION:
            case Action.SITUATION_WEB_VIEW_INVOCATION:
            case Action.SITUATION_PUSH_RECEIVED:
            case Action.SITUATION_AUTOMATION:
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
        ActionAutomation actionAutomation;
        try {
            actionAutomation = actionAutomationCallable.call();
        } catch (Exception e) {
            return ActionResult.newErrorResult(e);
        }

        try {
            ActionScheduleInfo info = ActionScheduleInfo.fromJson(arguments.getValue().toJsonValue());
            ActionSchedule schedule = actionAutomation.schedule(info).get();
            return schedule == null ? ActionResult.newEmptyResult() : ActionResult.newResult(ActionValue.wrap(schedule.getId()));
        } catch (JsonException | InterruptedException | ExecutionException e) {
            return ActionResult.newErrorResult(e);
        }
    }

}

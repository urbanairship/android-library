/* Copyright Airship and Contributors */

package com.urbanairship.automation.actions;

import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.automation.Audience;
import com.urbanairship.automation.InAppAutomation;
import com.urbanairship.automation.Schedule;
import com.urbanairship.automation.ScheduleDelay;
import com.urbanairship.automation.Trigger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.AirshipComponentUtils;
import com.urbanairship.util.DateUtils;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

/**
 * Action to schedule actions.
 * <p>
 * Accepted situations: SITUATION_MANUAL_INVOCATION, SITUATION_WEB_VIEW_INVOCATION,
 * SITUATION_AUTOMATION, and SITUATION_PUSH_RECEIVED.
 * <p>
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

    // JSON Keys
    private static final String ACTIONS_KEY = "actions";
    private static final String LIMIT_KEY = "limit";
    private static final String PRIORITY_KEY = "priority";
    private static final String GROUP_KEY = "group";
    private static final String END_KEY = "end";
    private static final String START_KEY = "start";
    private static final String DELAY_KEY = "delay";
    private static final String TRIGGERS_KEY = "triggers";
    private static final String INTERVAL_KEY = "interval";
    private static final String AUDIENCE_KEY = "audience";

    private final Callable<InAppAutomation> actionAutomationCallable;

    /**
     * Default constructor.
     */
    public ScheduleAction() {
        this(AirshipComponentUtils.callableForComponent(InAppAutomation.class));
    }

    /**
     * @hide
     */
    @VisibleForTesting
    ScheduleAction(@NonNull Callable<InAppAutomation> actionAutomationCallable) {
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
        InAppAutomation automation;
        try {
            automation = actionAutomationCallable.call();
        } catch (Exception e) {
            return ActionResult.newErrorResult(e);
        }

        try {
            Schedule<Actions> schedule = parseSchedule(arguments.getValue().toJsonValue());
            Boolean result = automation.schedule(schedule).get();
            return (result != null && result) ? ActionResult.newResult(ActionValue.wrap(schedule.getId())) : ActionResult.newEmptyResult();
        } catch (JsonException | InterruptedException | ExecutionException e) {
            return ActionResult.newErrorResult(e);
        }
    }

    @NonNull
    Schedule<Actions> parseSchedule(@NonNull JsonValue value) throws JsonException {
        JsonMap jsonMap = value.optMap();

        Schedule.Builder<Actions> builder = Schedule.newBuilder(new Actions(jsonMap.opt(ACTIONS_KEY).optMap()))
                                                    .setLimit(jsonMap.opt(LIMIT_KEY).getInt(1))
                                                    .setPriority(jsonMap.opt(PRIORITY_KEY).getInt(0))
                                                    .setGroup(jsonMap.opt(GROUP_KEY).getString());

        if (jsonMap.containsKey(END_KEY)) {
            builder.setEnd(DateUtils.parseIso8601(jsonMap.opt(END_KEY).optString(), -1));
        }

        if (jsonMap.containsKey(START_KEY)) {
            builder.setStart(DateUtils.parseIso8601(jsonMap.opt(START_KEY).optString(), -1));
        }

        for (JsonValue triggerJson : jsonMap.opt(TRIGGERS_KEY).optList()) {
            builder.addTrigger(Trigger.fromJson(triggerJson));
        }

        if (jsonMap.containsKey(DELAY_KEY)) {
            builder.setDelay(ScheduleDelay.fromJson(jsonMap.opt(DELAY_KEY)));
        }

        if (jsonMap.containsKey(INTERVAL_KEY)) {
            builder.setInterval(jsonMap.opt(INTERVAL_KEY).getLong(0), TimeUnit.SECONDS);
        }

        JsonValue audienceJson = jsonMap.opt(AUDIENCE_KEY).optMap().get(AUDIENCE_KEY);
        if (audienceJson != null) {
            builder.setAudience(Audience.fromJson(audienceJson));
        }

        try {
            return builder.build();
        } catch (IllegalArgumentException e) {
            throw new JsonException("Invalid schedule info", e);
        }
    }

}

/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.os.Bundle;
import android.os.Looper;

import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionCompletionCallback;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionRunRequestFactory;
import com.urbanairship.automation.actions.Actions;
import com.urbanairship.json.JsonValue;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Actions schedule delegate.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class ActionsScheduleDelegate implements ScheduleDelegate<Actions> {

    private final ActionRunRequestFactory actionRunRequestFactory;
    private final Map<String, Actions> actionsMap = new HashMap<>();

    ActionsScheduleDelegate(ActionRunRequestFactory actionRunRequestFactory) {
        this.actionRunRequestFactory = actionRunRequestFactory;
    }

    ActionsScheduleDelegate() {
        this(new ActionRunRequestFactory());
    }

    @Override
    public void onPrepareSchedule(@NonNull String scheduleId, Actions scheduleData, @NonNull AutomationDriver.PrepareScheduleCallback callback) {
        actionsMap.put(scheduleId, scheduleData);
        callback.onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
    }

    @Override
    public void onExecutionInvalidated(@NonNull String scheduleId) {
        actionsMap.remove(scheduleId);
    }

    @Override
    public void onExecute(@NonNull String scheduleId, @NonNull AutomationDriver.ExecutionCallback callback) {
        Actions actions = actionsMap.get(scheduleId);
        if (actions == null) {
            callback.onFinish();
            return;
        }

        Bundle metadata = new Bundle();
        metadata.putString(ActionArguments.ACTION_SCHEDULE_ID_METADATA, scheduleId);

        ActionCallback actionCallback = new ActionCallback(callback, actions.getActionsMap().size());
        for (Map.Entry<String, JsonValue> entry : actions.getActionsMap().entrySet()) {
            actionRunRequestFactory.createActionRequest(entry.getKey())
                                   .setValue(entry.getValue())
                                   .setSituation(Action.SITUATION_AUTOMATION)
                                   .setMetadata(metadata)
                                   .run(Looper.getMainLooper(), actionCallback);
        }
    }

    @Override
    @AutomationDriver.ReadyResult
    public int onCheckExecutionReadiness(@NonNull String scheduleId) {
        if (actionsMap.containsKey(scheduleId)) {
            return AutomationDriver.READY_RESULT_CONTINUE;
        } else {
            return AutomationDriver.READY_RESULT_INVALIDATE;
        }
    }


    /**
     * Helper class that calls the callback after all actions have run.
     */
    static class ActionCallback implements ActionCompletionCallback {

        private final AutomationDriver.ExecutionCallback callback;
        private int pendingActionCallbacks;

        /**
         * Default constructor.
         *
         * @param callback The completion callback.
         * @param pendingActionCallbacks Number of pending callbacks to expect.
         */
        ActionCallback(AutomationDriver.ExecutionCallback callback, int pendingActionCallbacks) {
            this.callback = callback;
            this.pendingActionCallbacks = pendingActionCallbacks;
        }

        @Override
        public void onFinish(@NonNull ActionArguments arguments, @NonNull ActionResult result) {
            pendingActionCallbacks--;
            if (pendingActionCallbacks == 0) {
                callback.onFinish();
            }
        }

    }
}

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
import com.urbanairship.experiment.ExperimentResult;
import com.urbanairship.json.JsonValue;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
    public void onNewSchedule(@NonNull Schedule<? extends ScheduleData> schedule) {
        // no-op
    }

    @Override
    public void onScheduleFinished(@NonNull Schedule<? extends ScheduleData> schedule) {
        // no-op
    }

    @Override
    public void onPrepareSchedule(
            @NonNull Schedule<? extends ScheduleData> schedule,
            @NonNull Actions scheduleData,
            @Nullable ExperimentResult experimentResult,
            @NonNull AutomationDriver.PrepareScheduleCallback callback) {
        actionsMap.put(schedule.getId(), scheduleData);
        callback.onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
    }

    @Override
    public void onExecutionInvalidated(@NonNull Schedule<? extends ScheduleData> schedule) {
        actionsMap.remove(schedule.getId());
    }

    @Override
    public void onExecutionInterrupted(@NonNull Schedule<? extends ScheduleData> schedule) {
        // no-op
    }

    @Override
    public void onExecute(@NonNull Schedule<? extends ScheduleData> schedule, @NonNull AutomationDriver.ExecutionCallback callback) {
        Actions actions = actionsMap.get(schedule.getId());
        if (actions == null) {
            callback.onFinish();
            return;
        }

        Bundle metadata = new Bundle();
        metadata.putString(ActionArguments.ACTION_SCHEDULE_ID_METADATA, schedule.getId());

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
    public int onCheckExecutionReadiness(@NonNull Schedule<? extends ScheduleData> schedule) {
        if (actionsMap.containsKey(schedule.getId())) {
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

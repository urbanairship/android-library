package com.urbanairship.automation;

import android.os.Bundle;
import android.os.Looper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionCompletionCallback;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.util.Map;

/**
 * Action automation driver for {@link AutomationEngine}
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class ActionAutomationDriver implements AutomationDriver<ActionSchedule> {

    @Override
    @MainThread
    public void onExecuteTriggeredSchedule(@NonNull ActionSchedule schedule, @NonNull ExecutionCallback finishCallback) {
        Bundle metadata = new Bundle();
        metadata.putParcelable(ActionArguments.ACTION_SCHEDULE_METADATA, schedule);

        ActionCallback actionCallback = new ActionCallback(finishCallback, schedule.getInfo().getActions().size());
        for (Map.Entry<String, JsonValue> entry : schedule.getInfo().getActions().entrySet()) {
            ActionRunRequest.createRequest(entry.getKey())
                            .setValue(entry.getValue())
                            .setSituation(Action.SITUATION_AUTOMATION)
                            .setMetadata(metadata)
                            .run(Looper.getMainLooper(), actionCallback);
        }
    }

    @NonNull
    @Override
    public ActionSchedule createSchedule(@NonNull String scheduleId, @NonNull JsonMap metadata, @NonNull ScheduleInfo info) {
        ActionScheduleInfo scheduleInfo = ActionScheduleInfo.newBuilder()
                                                            .setEnd(info.getEnd())
                                                            .setStart(info.getStart())
                                                            .setGroup(info.getGroup())
                                                            .setLimit(info.getLimit())
                                                            .setPriority(info.getPriority())
                                                            .addAllActions(info.getData().toJsonValue().optMap())
                                                            .setDelay(info.getDelay())
                                                            .addTriggers(info.getTriggers())
                                                            .build();

        return new ActionSchedule(scheduleId, metadata, scheduleInfo);
    }

    @Override
    public void onPrepareSchedule(@NonNull ActionSchedule schedule, @NonNull PrepareScheduleCallback callback) {
        callback.onFinish(PREPARE_RESULT_CONTINUE);
    }

    @ReadyResult
    @Override
    public int onCheckExecutionReadiness(@NonNull ActionSchedule schedule) {
        return READY_RESULT_CONTINUE;
    }

    /**
     * Helper class that calls the callback after all actions have run.
     */
    private static class ActionCallback implements ActionCompletionCallback {

        private final ExecutionCallback callback;
        private int pendingActionCallbacks;

        /**
         * Default constructor.
         *
         * @param callback The completion callback.
         * @param pendingActionCallbacks Number of pending callbacks to expect.
         */
        ActionCallback(ExecutionCallback callback, int pendingActionCallbacks) {
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

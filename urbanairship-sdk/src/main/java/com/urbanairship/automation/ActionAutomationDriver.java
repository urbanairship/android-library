package com.urbanairship.automation;

import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionCompletionCallback;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.json.JsonValue;

import java.util.Map;


/**
 * Action automation driver for {@link AutomationEngine}
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class ActionAutomationDriver implements AutomationDriver<ActionSchedule> {

    @MainThread
    @Override
    public void onExecuteTriggeredSchedule(ActionSchedule schedule, Callback finishCallback) {
        Bundle metadata = new Bundle();
        metadata.putParcelable(ActionArguments.ACTION_SCHEDULE_METADATA, schedule);

        ActionCallback actionCallback = new ActionCallback(finishCallback, schedule.getInfo().getActions().size());
        for (Map.Entry<String, JsonValue> entry : schedule.getInfo().getActions().entrySet()) {
            ActionRunRequest.createRequest(entry.getKey())
                            .setValue(entry.getValue())
                            .setSituation(Action.SITUATION_AUTOMATION)
                            .setMetadata(metadata)
                            .run(actionCallback, Looper.getMainLooper());
        }
    }

    @NonNull
    @Override
    public ActionSchedule createSchedule(String scheduleId, @NonNull ScheduleInfo info) {
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

        return new ActionSchedule(scheduleId, scheduleInfo);
    }


    @Override
    public boolean isScheduleReadyToExecute(ActionSchedule schedule) {
        return true;
    }

    /**
     * Helper class that calls the callback after all actions have run.
     */
    private static class ActionCallback implements ActionCompletionCallback {
        private final Callback callback;
        private int pendingActionCallbacks;

        /**
         * Default constructor.
         *
         * @param callback The completion callback.
         * @param pendingActionCallbacks Number of pending callbacks to expect.
         */
        ActionCallback(Callback callback, int pendingActionCallbacks) {
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

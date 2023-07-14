/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import com.urbanairship.experiment.ExperimentResult;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * In-App Message schedule delegate.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class InAppMessageScheduleDelegate implements ScheduleDelegate<InAppMessage> {

    private InAppMessageManager messageManager;

    public InAppMessageScheduleDelegate(InAppMessageManager messageManager) {
        this.messageManager = messageManager;
    }

    @Override
    public void onNewSchedule(@NonNull Schedule<? extends ScheduleData> schedule) {
        if (Schedule.TYPE_IN_APP_MESSAGE.equals(schedule.getType())) {
            messageManager.onNewMessageSchedule(schedule.getId(), (InAppMessage) schedule.coerceType());
        }
    }

    @Override
    public void onScheduleFinished(@NonNull Schedule<? extends ScheduleData> schedule) {
        messageManager.onMessageScheduleFinished(schedule.getId());
    }

    @Override
    public void onPrepareSchedule(
            @NonNull Schedule<? extends ScheduleData> schedule,
            @NonNull InAppMessage message,
            @Nullable ExperimentResult experimentResult,
            @NonNull AutomationDriver.PrepareScheduleCallback callback) {
        messageManager.onPrepare(schedule.getId(), schedule.getCampaigns(),
                schedule.getReportingContext(), message, experimentResult, callback);
    }

    @Override
    public void onExecutionInvalidated(@NonNull Schedule<? extends ScheduleData> schedule) {
        messageManager.onExecutionInvalidated(schedule.getId());
    }

    @Override
    public void onExecutionInterrupted(@NonNull Schedule<? extends ScheduleData> schedule) {
        InAppMessage message = Schedule.TYPE_IN_APP_MESSAGE.equals(schedule.getType()) ? (InAppMessage) schedule.coerceType() : null;
        messageManager.onExecutionInterrupted(schedule.getId(), schedule.getCampaigns(), schedule.getReportingContext(), message);
    }

    @Override
    public void onExecute(@NonNull Schedule<? extends ScheduleData> schedule, @NonNull AutomationDriver.ExecutionCallback callback) {
        messageManager.onExecute(schedule.getId(), callback);
    }

    @Override
    public int onCheckExecutionReadiness(@NonNull Schedule<? extends ScheduleData> schedule) {
        return messageManager.onCheckExecutionReadiness(schedule.getId());
    }

}

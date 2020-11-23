/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageManager;

import androidx.annotation.NonNull;
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
    public void onPrepareSchedule(@NonNull String scheduleId, InAppMessage message, @NonNull AutomationDriver.PrepareScheduleCallback callback) {
        messageManager.onPrepare(scheduleId, message, callback);
    }

    @Override
    public void onExecutionInvalidated(@NonNull String scheduleId) {
        messageManager.onExecutionInvalidated(scheduleId);
    }

    @Override
    public void onExecute(@NonNull String scheduleId, @NonNull AutomationDriver.ExecutionCallback callback) {
        messageManager.onExecute(scheduleId, callback);
    }

    @Override
    public int onCheckExecutionReadiness(@NonNull String scheduleId) {
        return messageManager.onCheckExecutionReadiness(scheduleId);
    }

}

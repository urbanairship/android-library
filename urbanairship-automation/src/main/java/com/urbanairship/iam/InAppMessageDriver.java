package com.urbanairship.iam;

import com.urbanairship.automation.AutomationDriver;
import com.urbanairship.automation.ParseScheduleException;
import com.urbanairship.automation.ScheduleInfo;
import com.urbanairship.json.JsonMap;

import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Automation driver for in-app messaging.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
abstract class InAppMessageDriver implements AutomationDriver<InAppMessageSchedule> {


    @NonNull
    @Override
    public InAppMessageSchedule createSchedule(@NonNull String scheduleId, @NonNull JsonMap metadata, @NonNull ScheduleInfo info) throws ParseScheduleException {
        try {
            InAppMessageScheduleInfo scheduleInfo = InAppMessageScheduleInfo.newBuilder()
                                                                            .addTriggers(info.getTriggers())
                                                                            .setDelay(info.getDelay())
                                                                            .setEnd(info.getEnd())
                                                                            .setStart(info.getStart())
                                                                            .setLimit(info.getLimit())
                                                                            .setPriority(info.getPriority())
                                                                            .setInterval(info.getInterval(), TimeUnit.MILLISECONDS)
                                                                            .setEditGracePeriod(info.getEditGracePeriod(), TimeUnit.MILLISECONDS)
                                                                            .setMessage(InAppMessage.fromJson(info.getData().toJsonValue()))
                                                                            .build();

            return new InAppMessageSchedule(scheduleId, metadata, scheduleInfo);
        } catch (Exception e) {
            throw new ParseScheduleException("Unable to parse in-app message for schedule: " + scheduleId + "info data: " + info.getData(), e);
        }
    }
}

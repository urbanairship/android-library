/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.PendingResult;
import com.urbanairship.automation.Schedule;
import com.urbanairship.automation.ScheduleData;
import com.urbanairship.automation.ScheduleEdits;
import com.urbanairship.automation.actions.Actions;

import java.util.Collection;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Interface for scheduling in-app automations.
 */
public interface InAppAutomationScheduler {

    /**
     * Schedules an in-app automation.
     *
     * @param schedule The in-app schedule.
     * @return A pending result. The result will be {@code true} if success,
     * otherwise {@code false}.
     */
    @NonNull
    PendingResult<Boolean> schedule(@NonNull Schedule<? extends ScheduleData> schedule);

    /**
     * Cancels an in-app schedule.
     *
     * @param scheduleId The in-app schedule ID.
     * @return A pending result. The result will be {@code true} if success,
     * otherwise {@code false}.
     */
    @NonNull
    PendingResult<Boolean> cancelSchedule(@NonNull String scheduleId);

    /**
     * Cancels in-app schedules by the group.
     *
     * @param group The schedule group.
     * @return A pending result.
     */
    @NonNull
    PendingResult<Boolean> cancelScheduleGroup(@NonNull String group);

    /**
     * Schedules a list of in-app automations.
     *
     * @param schedules The list of schedules.
     * @return A pending result. The result will be {@code true} if success,
     * otherwise {@code false}.
     */
    @NonNull
    PendingResult<Boolean> schedule(@NonNull List<Schedule<? extends ScheduleData>> schedules);

    /**
     * Gets action schedules by group.
     *
     * @param group The group.
     * @return A pending result.
     */
    @NonNull
    PendingResult<Collection<Schedule<Actions>>> getActionScheduleGroup(@NonNull String group);

    /**
     * Gets an action schedule by ID.
     *
     * @param scheduleId The schedule ID.
     * @return A pending result.
     */
    @NonNull
    PendingResult<Schedule<Actions>> getActionSchedule(@NonNull String scheduleId);

    /**
     * Gets all action schedules.
     *
     * @return A pending result.
     */
    @NonNull
    PendingResult<Collection<Schedule<Actions>>> getActionSchedules();


    /**
     * Gets message schedules by group.
     *
     * @param group The group.
     * @return A pending result.
     */
    @NonNull
    PendingResult<Collection<Schedule<InAppMessage>>> getMessageScheduleGroup(@NonNull String group);

    /**
     * Gets a message schedule by ID.
     *
     * @param scheduleId The schedule ID.
     * @return A pending result.
     */
    @NonNull
    PendingResult<Schedule<InAppMessage>> getMessageSchedule(@NonNull String scheduleId);

    /**
     * Gets all message schedules.
     *
     * @return A pending result.
     */
    @NonNull
    PendingResult<Collection<Schedule<InAppMessage>>> getMessageSchedules();

    /**
     * Gets all the schedules.
     *
     * @return A pending result.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    PendingResult<Collection<Schedule<? extends ScheduleData>>> getSchedules();

    /**
     * Edits an in-app schedule.
     *
     * @param scheduleId The schedule ID.
     * @param edits The edits.
     * @return A pending result with the result.
     */
    @NonNull
    PendingResult<Boolean> editSchedule(@NonNull String scheduleId, @NonNull ScheduleEdits<? extends ScheduleData> edits);

}

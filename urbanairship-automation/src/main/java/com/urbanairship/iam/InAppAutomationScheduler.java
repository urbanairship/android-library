/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.PendingResult;
import com.urbanairship.automation.Schedule;
import com.urbanairship.automation.ScheduleEdits;

import java.util.Collection;
import java.util.List;

import androidx.annotation.NonNull;

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
    PendingResult<Boolean> schedule(@NonNull Schedule schedule);

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
    PendingResult<Boolean> schedule(@NonNull List<Schedule> schedules);

    /**
     * Gets the schedules by group.
     *
     * @param group The group.
     * @return A pending result.
     */
    @NonNull
    PendingResult<Collection<Schedule>> getScheduleGroup(@NonNull String group);

    /**
     * Gets the schedule.
     *
     * @param scheduleId The schedule ID.
     * @return A pending result.
     */
    @NonNull
    PendingResult<Schedule> getSchedule(@NonNull String scheduleId);

    /**
     * Gets all the schedules.
     *
     * @return A pending result.
     */
    @NonNull
    PendingResult<Collection<Schedule>> getSchedules();

    /**
     * Edits an in-app schedule.
     *
     * @param scheduleId The schedule ID.
     * @param edits The edits.
     * @return A pending result with the updated schedule.
     */
    @NonNull
    PendingResult<Schedule> editSchedule(@NonNull String scheduleId, @NonNull ScheduleEdits edits);

}

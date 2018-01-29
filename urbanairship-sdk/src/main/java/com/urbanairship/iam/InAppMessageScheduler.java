/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.support.annotation.NonNull;

import com.urbanairship.PendingResult;

import java.util.Collection;
import java.util.List;

/**
 * Interface for scheduling in-app messages.
 */
public interface InAppMessageScheduler {

    /**
     * Schedules an in-app message.
     *
     * @param messageScheduleInfo The in-app message schedule info.
     * @return A pending result with the {@link InAppMessageSchedule}. The schedule may be nil if
     * the message's audience
     */
    PendingResult<InAppMessageSchedule> scheduleMessage(@NonNull InAppMessageScheduleInfo messageScheduleInfo);

    /**
     * Cancels an in-app message schedule.
     *
     * @param scheduleId The in-app message's schedule ID.
     * @return A pending result.
     */
    PendingResult<Void> cancelSchedule(@NonNull String scheduleId);

    /**
     * Cancels an in-app message schedule for the given message ID. If more than
     * one in-app message shares the same ID, they will all be cancelled.
     *
     * @param messageId The in-app message's ID.
     * @return A pending result.
     */
    PendingResult<Boolean> cancelMessage(@NonNull String messageId);

    /**
     * Message Ids that need to be cancelled.
     *
     * @param messageIds The list of message IDs.
     * @return A pending result.
     */
    PendingResult<Void> cancelMessages(@NonNull Collection<String> messageIds);

    /**
     * New schedules that need to be scheduled.
     *
     * @param scheduleInfos The list of schedule infos.
     * @return A pending result.
     */
    PendingResult<List<InAppMessageSchedule>> schedule(@NonNull List<InAppMessageScheduleInfo> scheduleInfos);

    /**
     * Gets the schedules associated with the message ID.
     *
     * @param messageId The message ID.
     * @return A pending result.
     */
    PendingResult<Collection<InAppMessageSchedule>> getSchedules(String messageId);

    /**
     * Gets the schedule.
     *
     * @param scheduleId The schedule ID.
     * @return A pending result.
     */
    PendingResult<InAppMessageSchedule> getSchedule(@NonNull String scheduleId);

    /**
     * Edits an in-app message schedule.
     *
     * @param scheduleId The schedule ID.
     * @param edits The edits.
     * @return A pending result with the updated schedule.
     */
    PendingResult<InAppMessageSchedule> editSchedule(@NonNull String scheduleId, @NonNull InAppMessageScheduleEdits edits);

}

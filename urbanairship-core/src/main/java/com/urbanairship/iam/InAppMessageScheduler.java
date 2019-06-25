/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import androidx.annotation.NonNull;

import com.urbanairship.PendingResult;
import com.urbanairship.json.JsonMap;

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
     * @return A pending result with the {@link InAppMessageSchedule}. The schedule may be null if
     * the message's audience
     */
    @NonNull
    PendingResult<InAppMessageSchedule> scheduleMessage(@NonNull InAppMessageScheduleInfo messageScheduleInfo);

    /**
     * Schedules an in-app message.
     *
     * @param messageScheduleInfo The in-app message schedule info.
     * @param metadata The schedule metadata.
     * @return A pending result with the {@link InAppMessageSchedule}. The schedule may be null if
     * the message's audience
     */
    @NonNull
    PendingResult<InAppMessageSchedule> scheduleMessage(@NonNull InAppMessageScheduleInfo messageScheduleInfo, @NonNull JsonMap metadata);

    /**
     * Cancels an in-app message schedule.
     *
     * @param scheduleId The in-app message's schedule ID.
     * @return A pending result.
     */
    @NonNull
    PendingResult<Void> cancelSchedule(@NonNull String scheduleId);

    /**
     * Cancels an in-app message schedule for the given message ID. If more than
     * one in-app message shares the same ID, they will all be cancelled.
     *
     * @param messageId The in-app message's ID.
     * @return A pending result.
     */
    @NonNull
    PendingResult<Boolean> cancelMessage(@NonNull String messageId);

    /**
     * Message Ids that need to be cancelled.
     *
     * @param messageIds The list of message IDs.
     * @return A pending result.
     */
    @NonNull
    PendingResult<Void> cancelMessages(@NonNull Collection<String> messageIds);

    /**
     * New schedules that need to be scheduled.
     *
     * @param scheduleInfos The list of schedule infos.
     * @return A pending result.
     */
    @NonNull
    PendingResult<List<InAppMessageSchedule>> schedule(@NonNull List<InAppMessageScheduleInfo> scheduleInfos);

    /**
     * New schedules that need to be scheduled.
     *
     * @param scheduleInfos The list of schedule infos.
     * @param metadata The schedule metadata.
     * @return A pending result.
     */
    @NonNull
    PendingResult<List<InAppMessageSchedule>> schedule(@NonNull List<InAppMessageScheduleInfo> scheduleInfos, @NonNull JsonMap metadata);

    /**
     * Gets the schedules associated with the message ID.
     *
     * @param messageId The message ID.
     * @return A pending result.
     */
    @NonNull
    PendingResult<Collection<InAppMessageSchedule>> getSchedules(@NonNull String messageId);

    /**
     * Gets the schedule.
     *
     * @param scheduleId The schedule ID.
     * @return A pending result.
     */
    @NonNull
    PendingResult<InAppMessageSchedule> getSchedule(@NonNull String scheduleId);

    /**
     * Gets all the schedules.
     *
     * @return A pending result.
     */
    @NonNull
    PendingResult<Collection<InAppMessageSchedule>> getSchedules();

    /**
     * Edits an in-app message schedule.
     *
     * @param scheduleId The schedule ID.
     * @param edits The edits.
     * @return A pending result with the updated schedule.
     */
    @NonNull
    PendingResult<InAppMessageSchedule> editSchedule(@NonNull String scheduleId, @NonNull InAppMessageScheduleEdits edits);

}

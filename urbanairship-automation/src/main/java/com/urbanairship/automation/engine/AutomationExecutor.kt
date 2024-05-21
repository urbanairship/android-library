/* Copyright Airship and Contributors */

package com.urbanairship.automation.engine

import androidx.annotation.MainThread
import com.urbanairship.UALog
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.isInAppMessageType
import com.urbanairship.iam.PreparedInAppMessageData
import com.urbanairship.automation.remotedata.AutomationRemoteDataAccess
import com.urbanairship.json.JsonValue

internal enum class ScheduleReadyResult {
    READY, INVALIDATE, NOT_READY, SKIP
}

internal enum class ScheduleExecuteResult {
    CANCEL, FINISHED, RETRY
}

internal enum class InterruptedBehavior {
    RETRY, FINISH
}

internal interface AutomationExecutorInterface {
    suspend fun isReadyPrecheck(schedule: AutomationSchedule): ScheduleReadyResult

    @MainThread
    fun isReady(preparedSchedule: PreparedSchedule): ScheduleReadyResult

    suspend fun execute(preparedSchedule: PreparedSchedule): ScheduleExecuteResult

    suspend fun interrupted(
        schedule: AutomationSchedule,
        preparedScheduleInfo: PreparedScheduleInfo
    ) : InterruptedBehavior
}

internal interface AutomationExecutorDelegate<ExecutionData> {

    @MainThread
    fun isReady(data: ExecutionData, preparedScheduleInfo: PreparedScheduleInfo): ScheduleReadyResult

    @MainThread
    suspend fun execute(data: ExecutionData, preparedScheduleInfo: PreparedScheduleInfo) : ScheduleExecuteResult
    suspend fun interrupted(schedule: AutomationSchedule, preparedScheduleInfo: PreparedScheduleInfo) : InterruptedBehavior
}

internal class AutomationExecutor(
    private val actionExecutor: AutomationExecutorDelegate<JsonValue>,
    private val messageExecutor: AutomationExecutorDelegate<PreparedInAppMessageData>,
    private val remoteDataAccess: AutomationRemoteDataAccess
) : AutomationExecutorInterface {

    override suspend fun isReadyPrecheck(schedule: AutomationSchedule): ScheduleReadyResult {
        if (!remoteDataAccess.isCurrent(schedule)) {
            return ScheduleReadyResult.INVALIDATE
        }

        return ScheduleReadyResult.READY
    }

    override fun isReady(preparedSchedule: PreparedSchedule): ScheduleReadyResult {
        if (preparedSchedule.frequencyChecker?.checkAndIncrement() == false) {
            return ScheduleReadyResult.SKIP
        }

        return when(preparedSchedule.data) {
            is PreparedScheduleData.Action -> {
                actionExecutor.isReady(preparedSchedule.data.json, preparedSchedule.info)
            }
            is PreparedScheduleData.InAppMessage -> {
                messageExecutor.isReady(preparedSchedule.data.message, preparedSchedule.info)
            }
        }
    }

    @MainThread
    override suspend fun execute(preparedSchedule: PreparedSchedule): ScheduleExecuteResult {
        return try {
            when(preparedSchedule.data) {
                is PreparedScheduleData.Action -> {
                    actionExecutor.execute(preparedSchedule.data.json, preparedSchedule.info)
                }

                is PreparedScheduleData.InAppMessage -> {
                    messageExecutor.execute(preparedSchedule.data.message, preparedSchedule.info)
                }
            }
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to execute automation: ${preparedSchedule.info.scheduleId}" }
            ScheduleExecuteResult.RETRY
        }
    }

    override suspend fun interrupted(
        schedule: AutomationSchedule,
        preparedScheduleInfo: PreparedScheduleInfo
    ): InterruptedBehavior {

        return if (schedule.isInAppMessageType()) {
            messageExecutor.interrupted(schedule, preparedScheduleInfo)
        } else {
            actionExecutor.interrupted(schedule, preparedScheduleInfo)
        }
    }
}

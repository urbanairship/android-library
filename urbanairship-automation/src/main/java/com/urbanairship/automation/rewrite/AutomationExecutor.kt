package com.urbanairship.automation.rewrite

import com.urbanairship.UALog
import com.urbanairship.automation.rewrite.engine.PreparedSchedule
import com.urbanairship.automation.rewrite.engine.PreparedScheduleData
import com.urbanairship.automation.rewrite.engine.PreparedScheduleInfo
import com.urbanairship.automation.rewrite.inappmessage.PreparedInAppMessageData
import com.urbanairship.automation.rewrite.remotedata.AutomationRemoteDataAccess
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
    suspend fun isReady(preparedSchedule: PreparedSchedule): ScheduleReadyResult
    suspend fun execute(preparedSchedule: PreparedSchedule): ScheduleExecuteResult
    suspend fun interrupted(
        schedule: AutomationSchedule,
        preparedScheduleInfo: PreparedScheduleInfo
    ) : InterruptedBehavior
}

internal interface AutomationExecutorDelegate<ExecutionData> {
    suspend fun isReady(data: ExecutionData, preparedScheduleInfo: PreparedScheduleInfo): ScheduleReadyResult
    suspend fun execute(data: ExecutionData, preparedScheduleInfo: PreparedScheduleInfo) : ScheduleExecuteResult
    suspend fun interrupted(schedule: AutomationSchedule, preparedScheduleInfo: PreparedScheduleInfo) : InterruptedBehavior
}

internal class AutomationExecutor(
    val actionExecutor: AutomationExecutorDelegate<JsonValue>,
    val messageExecutor: AutomationExecutorDelegate<PreparedInAppMessageData>,
    val remoteDataAccess: AutomationRemoteDataAccess
) : AutomationExecutorInterface {

    override suspend fun isReadyPrecheck(schedule: AutomationSchedule): ScheduleReadyResult {
        if (!remoteDataAccess.isCurrent(schedule)) {
            return ScheduleReadyResult.INVALIDATE
        }

        return ScheduleReadyResult.READY
    }

    override suspend fun isReady(preparedSchedule: PreparedSchedule): ScheduleReadyResult {
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
            UALog.e(ex) { "Failed to execute automation: ${preparedSchedule.info.scheduleID}" }
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

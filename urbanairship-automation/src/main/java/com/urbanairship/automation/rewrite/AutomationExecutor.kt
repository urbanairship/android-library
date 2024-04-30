package com.urbanairship.automation.rewrite

import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.automation.rewrite.engine.PreparedSchedule
import com.urbanairship.automation.rewrite.engine.PreparedScheduleData
import com.urbanairship.automation.rewrite.engine.PreparedScheduleInfo
import com.urbanairship.automation.rewrite.inappmessage.PreparedInAppMessageData
import com.urbanairship.automation.rewrite.remotedata.AutomationRemoteDataAccess
import com.urbanairship.json.JsonValue

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public enum class ScheduleReadyResult {
    READY, INVALIDATE, NOT_READY, SKIP
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public enum class ScheduleExecuteResult {
    CANCEL, FINISHED, RETRY
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public enum class InterruptedBehavior {
    RETRY, FINISH
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AutomationExecutorInterface {
    public suspend fun isReadyPrecheck(schedule: AutomationSchedule): ScheduleReadyResult

    @MainThread
    public fun isReady(preparedSchedule: PreparedSchedule): ScheduleReadyResult

    public suspend fun execute(preparedSchedule: PreparedSchedule): ScheduleExecuteResult

    public suspend fun interrupted(
        schedule: AutomationSchedule,
        preparedScheduleInfo: PreparedScheduleInfo
    ) : InterruptedBehavior
}

internal interface AutomationExecutorDelegate<ExecutionData> {

    @MainThread
    fun isReady(data: ExecutionData, preparedScheduleInfo: PreparedScheduleInfo): ScheduleReadyResult

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

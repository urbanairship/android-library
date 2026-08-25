/* Copyright Airship and Contributors */

package com.urbanairship.automation.engine

import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.json.JsonValue
import com.urbanairship.util.minus
import java.time.Instant
import java.util.Objects
import java.util.UUID
import kotlin.time.Duration.Companion.days
import org.jetbrains.annotations.VisibleForTesting

internal class AutomationScheduleData(
    schedule: AutomationSchedule,
    scheduleState: AutomationScheduleState,
    scheduleStateChangeDate: Instant,
    executionCount: Int,
    triggerInfo: TriggeringInfo? = null,
    preparedScheduleInfo: PreparedScheduleInfo? = null,
    var associatedData: JsonValue? = null,
    triggerSessionId: String
) {
    var schedule: AutomationSchedule = schedule
        private set
    var scheduleState: AutomationScheduleState = scheduleState
        private set
    var scheduleStateChangeDate: Instant = scheduleStateChangeDate
        private set
    var executionCount: Int = executionCount
        private set
    var triggerInfo: TriggeringInfo? = triggerInfo
        private set
    var preparedScheduleInfo: PreparedScheduleInfo? = preparedScheduleInfo
        private set

    var triggerSessionId: String = triggerSessionId
        private set

    internal fun setSchedule(schedule: AutomationSchedule): AutomationScheduleData {
        this.schedule = schedule
        return this
    }

    private fun setState(state: AutomationScheduleState, date: Instant): AutomationScheduleData {
        if (scheduleState == state) { return this }

        scheduleState = state
        scheduleStateChangeDate = date
        return this
    }

    internal fun finished(date: Instant): AutomationScheduleData {
        setState(AutomationScheduleState.FINISHED, date)
        preparedScheduleInfo = null
        triggerInfo = null
        return this
    }

    internal fun idle(date: Instant): AutomationScheduleData {
        setState(AutomationScheduleState.IDLE, date)
        preparedScheduleInfo = null
        triggerInfo = null
        return this
    }

    internal fun paused(date: Instant): AutomationScheduleData {
        setState(AutomationScheduleState.PAUSED, date)
        preparedScheduleInfo = null
        triggerInfo = null
        return this
    }

    internal fun triggered(triggerInfo: TriggeringInfo, date: Instant): AutomationScheduleData {
        if (scheduleState != AutomationScheduleState.IDLE) { return  this }

        if (isOverLimit() || isExpired(date)) {
            return finished(date)
        }

        preparedScheduleInfo = null
        this.triggerInfo = triggerInfo
        triggerSessionId = UUID.randomUUID().toString()

        return setState(AutomationScheduleState.TRIGGERED, date)
    }

    internal fun prepared(info: PreparedScheduleInfo, date: Instant): AutomationScheduleData {
        if (!isInState(listOf(AutomationScheduleState.TRIGGERED))) {
            return this
        }

        if (isOverLimit() || isExpired(date)) {
            return finished(date)
        }

        preparedScheduleInfo = info
        return setState(AutomationScheduleState.PREPARED, date)
    }

    internal fun executing(date: Instant): AutomationScheduleData {
        if (!isInState(listOf(AutomationScheduleState.PREPARED))) {
            return this
        }

        scheduleState = AutomationScheduleState.EXECUTING
        scheduleStateChangeDate = date
        return this
    }

    internal fun executionInterrupted(date: Instant, retry: Boolean): AutomationScheduleData {
        if (!isInState(listOf(AutomationScheduleState.EXECUTING))) {
            return this
        }

        if (!retry) {
            return finishedExecuting(date)
        }

        if (isOverLimit() || isExpired(date)) {
            return finished(date)
        }

        preparedScheduleInfo = null
        return setState(AutomationScheduleState.TRIGGERED, date)
    }

    internal fun executionCancelled(date: Instant): AutomationScheduleData {
        // Delay cancellation triggers are active for both `TRIGGERED` and `PREPARED`,
        // so a cancellation must unwind either state, including an in-flight prepare.
        if (!isInState(listOf(AutomationScheduleState.TRIGGERED, AutomationScheduleState.PREPARED))) {
            return this
        }

        if (isOverLimit() || isExpired(date)) {
            return finished(date)
        }

        return idle(date)
    }

    internal fun executionInvalidated(date: Instant): AutomationScheduleData {
        if (!isInState(listOf(AutomationScheduleState.PREPARED))) {
            return this
        }

        if (isOverLimit() || isExpired(date)) {
            return finished(date)
        }

        preparedScheduleInfo = null
        return setState(AutomationScheduleState.TRIGGERED, date)
    }

    internal fun executionSkipped(date: Instant): AutomationScheduleData {
        if (!isInState(listOf(AutomationScheduleState.PREPARED))) {
            return this
        }

        if (isOverLimit() || isExpired(date)) {
            return finished(date)
        }

        return if (schedule.interval != null) {
            paused(date)
        } else {
            idle(date)
        }
    }

    internal fun prepareCancelled(date: Instant, penalize: Boolean): AutomationScheduleData {
        if (!isInState(listOf(AutomationScheduleState.TRIGGERED))) {
            return this
        }

        if (penalize){
            executionCount += 1
        }

        if (isOverLimit() || isExpired(date)) {
            return finished(date)
        }

        return idle(date)
    }

    internal fun prepareInterrupted(date: Instant): AutomationScheduleData {
        if (!isInState(listOf(AutomationScheduleState.PREPARED, AutomationScheduleState.TRIGGERED))) {
            return this
        }

        if (isOverLimit() || isExpired(date)) {
            return finished(date)
        }

        return setState(AutomationScheduleState.TRIGGERED, date)
    }

    internal fun finishedExecuting(date: Instant): AutomationScheduleData {
        if (!isInState(listOf(AutomationScheduleState.EXECUTING))) {
            return this
        }

        executionCount += 1

        if (isOverLimit() || isExpired(date)) {
            finished(date)
            return this
        }

        return if (schedule.interval == null) {
            idle(date)
        } else {
            paused(date)
        }
    }

    internal fun updateState(timeStamp: Instant): AutomationScheduleData {
        return if (isOverLimit() || isExpired(timeStamp)) {
            finished(timeStamp)
        } else if (isInState(listOf(AutomationScheduleState.FINISHED))) {
            idle(timeStamp)
        } else {
            this
        }
    }

    internal fun shouldDelete(date: Instant): Boolean {
        if (scheduleState != AutomationScheduleState.FINISHED) {
            return false
        }

        val gracePeriod = (schedule.editGracePeriodDays ?: return true).toLong().days
        val sinceLastChange = date - scheduleStateChangeDate
        return sinceLastChange >= gracePeriod
    }

    internal fun isExpired(date: Instant): Boolean {
        val end = schedule.endDate ?: return false
        return end <= date
    }

    internal fun isActive(date: Instant): Boolean {
        if (isExpired(date)) { return false }
        val start = schedule.startDate ?: return true
        return date >= start
    }

    internal fun isOverLimit(): Boolean {
        // 0 means no limit
        val limit = schedule.limit ?: 1U
        if (limit == 0U) { return false }

        return limit <= executionCount.toUInt()
    }

    internal fun isInState(state: List<AutomationScheduleState>): Boolean {
        return state.contains(scheduleState)
    }

    internal class Comparator(val date: Instant) : java.util.Comparator<AutomationScheduleData> {

        override fun compare(left: AutomationScheduleData, right: AutomationScheduleData): Int {
            val leftPriority = left.schedule.priority ?: 0
            val rightPriority = right.schedule.priority ?: 0

            if (leftPriority != rightPriority) {
                return leftPriority.compareTo(rightPriority)
            }

            val leftDate = left.triggerInfo?.date ?: date
            val rightDate = right.triggerInfo?.date ?: date
            return leftDate.compareTo(rightDate)
        }
    }

    @VisibleForTesting
    internal fun setExecutionCount(count: Int) {
        executionCount = count
    }

    @VisibleForTesting
    internal fun setTriggeringInfo(info: TriggeringInfo) {
        triggerInfo = info
    }

    @VisibleForTesting
    internal fun setPreparedScheduleInfo(data: PreparedScheduleInfo) {
        preparedScheduleInfo = data
    }


    override fun hashCode(): Int {
        return Objects.hash(schedule, scheduleState, scheduleStateChangeDate, executionCount,
            triggerInfo, preparedScheduleInfo)
    }

    override fun toString(): String {
        return "AutomationScheduleData(scheduleId=${schedule.identifier}, scheduleState=$scheduleState)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AutomationScheduleData

        if (schedule != other.schedule) return false
        if (scheduleState != other.scheduleState) return false
        if (scheduleStateChangeDate != other.scheduleStateChangeDate) return false
        if (executionCount != other.executionCount) return false
        if (triggerInfo != other.triggerInfo) return false
        if (preparedScheduleInfo != other.preparedScheduleInfo) return false

        return true
    }
}

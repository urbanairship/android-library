package com.urbanairship.automation.rewrite

import com.urbanairship.automation.rewrite.engine.AutomationScheduleState
import com.urbanairship.automation.rewrite.engine.PreparedSchedule
import com.urbanairship.automation.rewrite.engine.PreparedScheduleInfo
import com.urbanairship.automation.rewrite.engine.TriggeringInfo
import com.urbanairship.deferred.DeferredTriggerContext
import java.util.Objects
import java.util.concurrent.TimeUnit
import org.jetbrains.annotations.VisibleForTesting

internal class AutomationScheduleData(
    schedule: AutomationSchedule,
    scheduleState: AutomationScheduleState,
    scheduleStateChangeDate: Long,
    executionCount: Int,
    triggerInfo: TriggeringInfo? = null,
    preparedScheduleInfo: PreparedScheduleInfo? = null
) {
    var schedule: AutomationSchedule = schedule
        private set
    var scheduleState: AutomationScheduleState = scheduleState
        private set
    var scheduleStateChangeDate: Long = scheduleStateChangeDate
        private set
    var executionCount: Int = executionCount
        private set
    var triggerInfo: TriggeringInfo? = triggerInfo
        private set
    var preparedScheduleInfo: PreparedScheduleInfo? = preparedScheduleInfo
        private set

    fun setSchedule(schedule: AutomationSchedule) {
        this.schedule = schedule
    }

    private fun setState(state: AutomationScheduleState, date: Long): AutomationScheduleData {
        if (scheduleState == state) { return this }

        scheduleState = state
        scheduleStateChangeDate = date
        return this
    }

    fun finished(date: Long): AutomationScheduleData {
        setState(AutomationScheduleState.FINISHED, date)
        preparedScheduleInfo = null
        triggerInfo = null
        return this
    }

    fun idle(date: Long): AutomationScheduleData {
        setState(AutomationScheduleState.IDLE, date)
        preparedScheduleInfo = null
        triggerInfo = null
        return this
    }

    fun paused(date: Long): AutomationScheduleData {
        setState(AutomationScheduleState.PAUSED, date)
        preparedScheduleInfo = null
        triggerInfo = null
        return this
    }

    fun triggered(context: DeferredTriggerContext?, date: Long): AutomationScheduleData {
        if (scheduleState != AutomationScheduleState.IDLE) { return  this }

        if (isOverLimit() || isExpired(date)) {
            return finished(date)
        }

        preparedScheduleInfo = null
        triggerInfo = TriggeringInfo(
            context = context,
            date = date
        )

        return setState(AutomationScheduleState.TRIGGERED, date)
    }

    fun prepared(info: PreparedScheduleInfo, date: Long): AutomationScheduleData {
        if (!isInState(listOf(AutomationScheduleState.TRIGGERED))) {
            return this
        }

        if (isOverLimit() || isExpired(date)) {
            return finished(date)
        }

        preparedScheduleInfo = info
        return setState(AutomationScheduleState.PREPARED, date)
    }

    fun executing(date: Long): AutomationScheduleData {
        if (!isInState(listOf(AutomationScheduleState.PREPARED))) {
            return this
        }

        scheduleState = AutomationScheduleState.EXECUTING
        scheduleStateChangeDate = date
        return this
    }

    fun executionInterrupted(date: Long, retry: Boolean): AutomationScheduleData {
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

    fun executionCancelled(date: Long): AutomationScheduleData {
        if (!isInState(listOf(AutomationScheduleState.PREPARED))) {
            return this
        }

        if (isOverLimit() || isExpired(date)) {
            return finished(date)
        }

        return idle(date)
    }

    fun executionInvalidated(date: Long): AutomationScheduleData {
        if (!isInState(listOf(AutomationScheduleState.PREPARED))) {
            return this
        }

        if (isOverLimit() || isExpired(date)) {
            return finished(date)
        }

        preparedScheduleInfo = null
        return setState(AutomationScheduleState.TRIGGERED, date)
    }

    fun executionSkipped(date: Long): AutomationScheduleData {
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

    fun prepareCancelled(date: Long, penalize: Boolean): AutomationScheduleData {
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

    fun prepareInterrupted(date: Long): AutomationScheduleData {
        if (!isInState(listOf(AutomationScheduleState.PREPARED, AutomationScheduleState.TRIGGERED))) {
            return this
        }

        if (isOverLimit() || isExpired(date)) {
            return finished(date)
        }

        return setState(AutomationScheduleState.TRIGGERED, date)
    }

    fun finishedExecuting(date: Long): AutomationScheduleData {
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

    fun updateState(timeStamp: Long): AutomationScheduleData {
        return if (isOverLimit() || isExpired(timeStamp)) {
            finished(timeStamp)
        } else if (isInState(listOf(AutomationScheduleState.FINISHED))) {
            idle(timeStamp)
        } else {
            this
        }
    }

    fun shouldDelete(date: Long): Boolean {
        if (scheduleState != AutomationScheduleState.FINISHED) {
            return false
        }

        val gracePeriod = schedule.editGracePeriodDays ?: return true
        val sinceLastChange = date - scheduleStateChangeDate
        return sinceLastChange >= TimeUnit.DAYS.toMillis(gracePeriod.toLong())
    }

    fun isExpired(date: Long): Boolean {
        val end = schedule.endDate ?: return false
        return end <= date.toULong()
    }

    fun isActive(date: Long): Boolean {
        if (isExpired(date)) { return false }
        val start = schedule.startDate ?: return true
        return date >= start.toLong()
    }

    fun isOverLimit(): Boolean {
        // 0 means no limit
        val limit = schedule.limit ?: 1U
        if (limit == 0U) { return false }

        return limit <= executionCount.toUInt()
    }

    fun isInState(state: List<AutomationScheduleState>): Boolean {
        return state.contains(scheduleState)
    }

    class Comparator(val date: Long) : java.util.Comparator<AutomationScheduleData> {

        override fun compare(left: AutomationScheduleData, right: AutomationScheduleData): Int {
            val leftPriority = left.schedule.priority ?: 0
            val rightPriority = right.schedule.priority ?: 0

            if (leftPriority < rightPriority) {
                return 1
            }

            val leftDate = left.triggerInfo?.date ?: date
            val rightDate = right.triggerInfo?.date ?: date
            return leftDate.compareTo(rightDate)
        }
    }

    @VisibleForTesting
    fun setExecutionCount(count: Int) {
        executionCount = count
    }

    @VisibleForTesting
    fun setTrigerringInfo(info: TriggeringInfo) {
        triggerInfo = info
    }

    @VisibleForTesting
    fun setPreparedScheduleInfo(data: PreparedScheduleInfo) {
        preparedScheduleInfo = data
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
        return preparedScheduleInfo == other.preparedScheduleInfo
    }

    override fun hashCode(): Int {
        return Objects.hash(schedule, scheduleState, scheduleStateChangeDate, executionCount,
            triggerInfo, preparedScheduleInfo)
    }

}

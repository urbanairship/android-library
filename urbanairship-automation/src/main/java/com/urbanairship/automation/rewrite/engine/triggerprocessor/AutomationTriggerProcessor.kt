/* Copyright Airship and Contributors */

package com.urbanairship.automation.rewrite.engine.triggerprocessor

import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.automation.rewrite.AutomationEvent
import com.urbanairship.automation.rewrite.AutomationSchedule
import com.urbanairship.automation.rewrite.AutomationScheduleData
import com.urbanairship.automation.rewrite.AutomationTrigger
import com.urbanairship.automation.rewrite.TriggerStoreInterface
import com.urbanairship.automation.rewrite.TriggerableState
import com.urbanairship.automation.rewrite.engine.AutomationScheduleState
import com.urbanairship.automation.rewrite.engine.TriggeringInfo
import com.urbanairship.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class AutomationTriggerProcessor(
    private val store: TriggerStoreInterface,
    private val clock: Clock = Clock.DEFAULT_CLOCK
) {
    private val triggerResultsFlow = MutableSharedFlow<TriggerResult>()
    private val isPausedFlow = MutableStateFlow(false)

    private var preparedTriggers = mutableMapOf<String, List<PreparedTrigger>>()
    private var scheduleGroups = mutableMapOf<String, String>()
    private var appSessionState: TriggerableState? = null

    fun setPaused(paused: Boolean) {
        isPausedFlow.update { paused }
    }

    suspend fun getTriggerResults() : Flow<TriggerResult> = triggerResultsFlow

    suspend fun processEvent(event: AutomationEvent) {

        trackStateChange(event)

        if (isPausedFlow.value) {
            return
        }

        val results = mutableListOf<PreparedTrigger.EventProcessResult>()

        this.preparedTriggers.values.forEach { triggers ->
            triggers.forEach{ it.process(event)?.let(results::add)}
        }

        for (item in results.sortedWith(compareBy { it.priority })) {
            val result = item.triggerResult ?: continue
            emit(result)
        }

        val triggerDatas = results.map { it.triggerData }
        try {
            store.upsertTriggers(triggerDatas)
        } catch (e: Exception) {
            UALog.e("Error trying to insert triggers $triggerDatas with error : ${e.message}")
        }
    }

    /**
     * Called once to update all schedules from the DB.
     */
    suspend fun restoreSchedules(datas: List<AutomationScheduleData>) {
        updateSchedules(datas)
        val activeSchedules = datas.map { it.schedule.identifier }
        store.deleteTriggersExcluding(activeSchedules)
    }

    /**
     * Called whenever the schedules are updated
     */
    suspend fun updateSchedules(datas: List<AutomationScheduleData>) {
        val sorted = datas.sortedWith(compareBy { it.schedule.priority })

        for (data in sorted) {
            val schedule = data.schedule
            schedule.group?.let { scheduleGroups[schedule.identifier] = it }

            val new = mutableListOf<PreparedTrigger>()
            val old = preparedTriggers[schedule.identifier] ?: emptyList()

            for (trigger in schedule.triggers) {
                val existing = old.firstOrNull { it.trigger.id == trigger.id }

                if (existing != null) {
                    existing.update(
                        trigger = trigger,
                        startDate = schedule.startDate,
                        endDate = schedule.endDate,
                        priority = schedule.priority ?: 0
                    )
                    new.add(existing)
                } else {
                    val prepared = makePreparedTrigger(schedule, trigger, TriggerExecutionType.EXECUTION)
                    new.add(prepared)
                }
            }

            val cancellationTriggeres = schedule.delay?.cancellationTriggers ?: emptyList()

            for (trigger in cancellationTriggeres) {
                val existing = old.firstOrNull { it.trigger.id == trigger.id }

                if (existing != null) {
                    existing.update(
                        trigger = trigger,
                        startDate = schedule.startDate,
                        endDate = schedule.endDate,
                        priority = schedule.priority ?: 0
                    )
                    new.add(existing)
                } else {
                    val prepared = makePreparedTrigger(schedule, trigger, TriggerExecutionType.DELAY_CANCELLATION)
                    new.add(prepared)
                }
            }

            preparedTriggers[schedule.identifier] = new
            val newIds = new.map { it.trigger.id }.toSet()
            val oldIds = old.map { it.trigger.id }.toSet()

            try {
                val stale = oldIds.subtract(newIds)
                if (stale.isNotEmpty()) {
                    store.deleteTriggers(schedule.identifier, stale)
                }
            } catch (e: Exception) {
                UALog.e("Failed to delete trigger states error : ${e.message}")
            }

            this.updateScheduleState(schedule.identifier, data.scheduleState)
        }
    }

    suspend fun cancel(scheduleIds: List<String>) {
        scheduleIds.forEach {
            this.preparedTriggers.remove(it)
            this.scheduleGroups.remove(it)
        }

        store.deleteTriggers(scheduleIds)
    }

    suspend fun cancel(group: String) {
        val scheduleIds = this.scheduleGroups.filter { it.value == group }.map { it.key }
        cancel(scheduleIds)
    }

    private fun trackStateChange(event: AutomationEvent) {
        when (event) {
            is AutomationEvent.StateChanged -> this.appSessionState = event.state
            else -> return
        }
    }

    suspend fun updateScheduleState(scheduleId: String, state: AutomationScheduleState) {
        when (state) {
            AutomationScheduleState.IDLE -> {
                this.activateTriggers(scheduleId, TriggerExecutionType.EXECUTION)
            }
            AutomationScheduleState.TRIGGERED, AutomationScheduleState.PREPARED -> {
                this.activateTriggers(scheduleId, TriggerExecutionType.DELAY_CANCELLATION)
            }
            AutomationScheduleState.PAUSED, AutomationScheduleState.FINISHED -> {
                this.disableTriggers(scheduleId)
            }
            else -> return
        }
    }

    private suspend fun activateTriggers(scheduleId: String, type: TriggerExecutionType) {
        val triggers = preparedTriggers[scheduleId] ?: return

        triggers.forEach { it.activate() }

        val sessionState = appSessionState ?: return

        val results = triggers.mapNotNull {
            it.process(AutomationEvent.StateChanged(sessionState))
        }

        for (item in results) {
            val result = item.triggerResult ?: continue
            emit(result)
        }

        store.upsertTriggers(results.map { it.triggerData })
    }

    private fun disableTriggers(scheduleId: String) {
        this.preparedTriggers[scheduleId]?.forEach {
            it.disable()
        }
    }

    private suspend fun emit(result: TriggerResult) {
        if (isPausedFlow.value) {
            return
        }

        triggerResultsFlow.emit(result)
    }

    private suspend fun makePreparedTrigger(
        schedule: AutomationSchedule,
        trigger: AutomationTrigger,
        type: TriggerExecutionType
    ) : PreparedTrigger {
        val triggerData = try {
            store.getTrigger(schedule.identifier, trigger.id)
        } catch (e: Exception) {
            UALog.e(e) {
                "Failed to get trigger for schedule ${schedule.identifier} trigger ${trigger.id}"
            }
            null
        } ?: TriggerData(schedule.identifier, trigger.id)

        return PreparedTrigger(
            scheduleId = schedule.identifier,
            executionType = type,
            triggerData = triggerData,
            trigger = trigger,
            startDate = schedule.startDate,
            endDate = schedule.endDate,
            priority = schedule.priority ?: 0,
            clock = clock
        )
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class TriggerResult(
    val scheduleId: String,
    var triggerExecutionType: TriggerExecutionType,
    var triggerInfo: TriggeringInfo
)

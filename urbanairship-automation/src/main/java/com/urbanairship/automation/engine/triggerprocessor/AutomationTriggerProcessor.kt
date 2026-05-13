/* Copyright Airship and Contributors */

package com.urbanairship.automation.engine.triggerprocessor

import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.AutomationTrigger
import com.urbanairship.automation.engine.AutomationEvent
import com.urbanairship.automation.engine.AutomationScheduleData
import com.urbanairship.automation.engine.AutomationScheduleState
import com.urbanairship.automation.engine.EventsHistory
import com.urbanairship.automation.engine.TriggerStoreInterface
import com.urbanairship.automation.engine.TriggerableState
import com.urbanairship.automation.engine.TriggeringInfo
import com.urbanairship.util.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

internal class AutomationTriggerProcessor(
    private val store: TriggerStoreInterface,
    private val history: EventsHistory,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    private val dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher()
) {
    private val triggerResultsFlow = MutableSharedFlow<TriggerResult>()
    private val isPausedFlow = MutableStateFlow(false)

    private val preparedTriggers = mutableMapOf<String, List<PreparedTrigger>>()
    private val scheduleGroups = mutableMapOf<String, String>()
    private var appSessionState: TriggerableState? = null

    fun setPaused(paused: Boolean) {
        isPausedFlow.update { paused }
    }

    fun getTriggerResults() : Flow<TriggerResult> = triggerResultsFlow

    suspend fun processEvent(event: AutomationEvent): Unit = withContext(dispatcher) {
        ingestEvent(event, preparedTriggers.values.flatten())
    }

    // Assumes caller is on [dispatcher]. Touches appSessionState via trackStateChange.
    private suspend fun ingestEvent(event: AutomationEvent, triggers: List<PreparedTrigger>, isReplay: Boolean = false) {
        if (!isReplay) {
            trackStateChange(event)
        }

        if (isPausedFlow.value) {
            return
        }

        val results = triggers.mapNotNull { it.process(event) }
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
    suspend fun restoreSchedules(datas: List<AutomationScheduleData>): Unit = withContext(dispatcher) {
        updateSchedules(datas)
        val activeSchedules = datas.map { it.schedule.identifier }
        store.deleteTriggersExcluding(activeSchedules)
    }

    /**
     * Called whenever the schedules are updated
     */
    suspend fun updateSchedules(datas: List<AutomationScheduleData>): Unit = withContext(dispatcher) {
        val sorted = datas.sortedWith(compareBy { it.schedule.priority })

        val allNewTriggers = mutableListOf<PreparedTrigger>()

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
                    val prepared = makePreparedTrigger(schedule, trigger,
                        TriggerExecutionType.EXECUTION
                    )
                    new.add(prepared)
                    allNewTriggers.add(prepared)
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
                    val prepared = makePreparedTrigger(schedule, trigger,
                        TriggerExecutionType.DELAY_CANCELLATION
                    )
                    new.add(prepared)
                    allNewTriggers.add(prepared)
                }
            }

            preparedTriggers[schedule.identifier] = new.toList()

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

            updateScheduleState(schedule.identifier, data.scheduleState)
        }

        if (allNewTriggers.isNotEmpty()) {
            history.getEvents().forEach { event ->
                ingestEvent(event, allNewTriggers, isReplay = true)
            }
        }
    }

    suspend fun cancel(scheduleIds: List<String>): Unit = withContext(dispatcher) {
        removeSchedules(scheduleIds)
    }

    suspend fun cancel(group: String): Unit = withContext(dispatcher) {
        val scheduleIds = scheduleGroups.filter { it.value == group }.map { it.key }
        removeSchedules(scheduleIds)
    }

    // Assumes caller is on [dispatcher]. Mutates preparedTriggers and scheduleGroups.
    private suspend fun removeSchedules(scheduleIds: List<String>) {
        scheduleIds.forEach {
            preparedTriggers.remove(it)
            scheduleGroups.remove(it)
        }

        store.deleteTriggers(scheduleIds)
    }

    // Assumes caller is on [dispatcher]. Mutates appSessionState.
    private fun trackStateChange(event: AutomationEvent) {
        when (event) {
            is AutomationEvent.StateChanged -> this.appSessionState = event.state
            else -> return
        }
    }

    suspend fun updateScheduleState(scheduleId: String, state: AutomationScheduleState): Unit = withContext(dispatcher) {
        when (state) {
            AutomationScheduleState.IDLE -> {
                updateActiveTriggers(scheduleId, type = TriggerExecutionType.EXECUTION)
            }
            AutomationScheduleState.TRIGGERED, AutomationScheduleState.PREPARED -> {
                updateActiveTriggers(scheduleId, type = TriggerExecutionType.DELAY_CANCELLATION)
            }
            AutomationScheduleState.EXECUTING, AutomationScheduleState.PAUSED, AutomationScheduleState.FINISHED -> {
                updateActiveTriggers(scheduleId, type = null)
            }
        }
    }

    // Assumes caller is on [dispatcher]. Reads preparedTriggers and appSessionState.
    private suspend fun updateActiveTriggers(scheduleId: String, type: TriggerExecutionType?) {
        val triggers = preparedTriggers[scheduleId] ?: return
        if (type == null) {
            triggers.forEach { it.disable() }
            return
        }

        triggers.forEach {
            if (it.executionType == type) {
                it.activate()
            } else {
                it.disable()
            }
        }

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

internal data class TriggerResult(
    val scheduleId: String,
    var triggerExecutionType: TriggerExecutionType,
    var triggerInfo: TriggeringInfo
)

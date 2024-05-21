package com.urbanairship.automation.storage

import com.urbanairship.UALog
import com.urbanairship.automation.AutomationAppState
import com.urbanairship.automation.AutomationAudience
import com.urbanairship.automation.AutomationDelay
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.AutomationScheduleData
import com.urbanairship.automation.AutomationStoreInterface
import com.urbanairship.automation.AutomationTrigger
import com.urbanairship.automation.EventAutomationTrigger
import com.urbanairship.automation.EventAutomationTriggerType
import com.urbanairship.automation.engine.AutomationScheduleState
import com.urbanairship.automation.engine.PreparedScheduleInfo
import com.urbanairship.automation.engine.TriggeringInfo
import com.urbanairship.automation.engine.triggerprocessor.TriggerData
import com.urbanairship.automation.engine.triggerprocessor.TriggerExecutionType
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import java.util.concurrent.TimeUnit

internal class AutomationStoreMigrator(
    private val legacyDatabase: AutomationDatabase,
    private val store: AutomationStoreInterface
) {

    internal suspend fun migrateData() {
        val legacyDao = legacyDatabase.scheduleDao

        val oldSchedules = legacyDao.schedules
        if (oldSchedules.isEmpty()) return

        val converted = convert(oldSchedules)
        if (converted.isNotEmpty()) {
            val map = converted.associateBy { it.scheduleData.schedule.identifier }
            store.upsertSchedules(map.keys.toList()) { id, _ ->
                requireNotNull(map[id]?.scheduleData)
            }
            store.upsertTriggers(converted.flatMap { it.triggerData })
        }
        legacyDao.deleteSchedules(oldSchedules)
    }

    private fun convert(fullSchedules: List<FullSchedule>): List<Converted> {
        return fullSchedules.mapNotNull { fullSchedule ->
            try {
                val scheduleData = getScheduleData(fullSchedule.schedule) ?: return@mapNotNull null

                val automationSchedule = AutomationSchedule(
                    identifier = fullSchedule.schedule.scheduleId,
                    data = scheduleData,
                    triggers = getTriggers(fullSchedule, TriggerExecutionType.EXECUTION),
                    startDate = fullSchedule.schedule.scheduleStart.let {
                        if (it >= 0) { it.toULong() } else { null }
                    },
                    endDate = fullSchedule.schedule.scheduleEnd.let {
                        if (it >= 0) { it.toULong() } else { null }
                    },
                    created = fullSchedule.schedule.newUserEvaluationDate.toULong(),
                    group = fullSchedule.schedule.group,
                    priority = fullSchedule.schedule.priority,
                    limit = fullSchedule.schedule.limit.let {
                        if (it >= 0) { it.toUInt() } else { null }
                    },
                    interval = fullSchedule.schedule.interval.toULong(),
                    delay = getDelay(fullSchedule),
                    metadata = fullSchedule.schedule.metadata?.toJsonValue(),
                    campaigns = fullSchedule.schedule.campaigns,
                    editGracePeriodDays = TimeUnit.MILLISECONDS.toDays(fullSchedule.schedule.editGracePeriod).toULong(),
                    productId = fullSchedule.schedule.productId,
                    frequencyConstraintIds = fullSchedule.schedule.frequencyConstraintIds?.ifEmpty { null },
                    messageType = fullSchedule.schedule.messageType,
                    audience = fullSchedule.schedule.audience?.let {
                        AutomationAudience.fromJson(JsonValue.parseString(it))
                    },
                    bypassHoldoutGroups = fullSchedule.schedule.bypassHoldoutGroups,
                    reportingContext = fullSchedule.schedule.reportingContext
                )

                Converted(
                    AutomationScheduleData(
                        automationSchedule,
                        convertScheduleState(fullSchedule.schedule.executionState),
                        fullSchedule.schedule.executionStateChangeDate,
                        fullSchedule.schedule.count,
                        getTriggeringInfo(fullSchedule.schedule),
                        getPreparedScheduleInfo(fullSchedule.schedule)
                    ), convertTriggers(fullSchedule.triggers)
                )
            } catch (e: Exception) {
                UALog.e(e) { "Failed to convert schedule."}
                null
            }
        }
    }

    private fun getScheduleData(entity: ScheduleEntity): AutomationSchedule.ScheduleData? {
        entity.data.map?.let {
            var jsonBuilder = JsonMap.newBuilder().putAll(it)
            jsonBuilder.put("type", JsonValue.wrap(entity.scheduleType))

            if (entity.scheduleType == AutomationSchedule.ScheduleType.IN_APP_MESSAGE.json) {
                jsonBuilder.put("message", it)
            }
            if (entity.scheduleType == AutomationSchedule.ScheduleType.DEFERRED.json) {
                jsonBuilder.put("deferred", it)
            }
            if (entity.scheduleType == AutomationSchedule.ScheduleType.ACTIONS.json) {
                jsonBuilder.put("actions", it)
            }

            return AutomationSchedule.ScheduleData.fromJson(jsonBuilder.build().toJsonValue())
        } ?: run {
            UALog.e("Failed to parse scheduleEntity, map is null")
            throw Exception()
        }
    }

    private fun getDelay(fullSchedule: FullSchedule): AutomationDelay {
        return AutomationDelay(
            seconds = fullSchedule.schedule.seconds,
            screens = fullSchedule.schedule.screens.ifEmpty { null },
            regionId = fullSchedule.schedule.regionId,
            appState = fullSchedule.schedule.appState.let {
                when(it) {
                    1 -> null
                    2 -> AutomationAppState.FOREGROUND
                    3 -> AutomationAppState.BACKGROUND
                    else -> {
                        UALog.e { "Unexpected app state $it "}
                        null
                    }
                }
            },
            cancellationTriggers = getTriggers(fullSchedule, TriggerExecutionType.DELAY_CANCELLATION).ifEmpty { null }
        )
    }

    private fun getTriggers(fullSchedule: FullSchedule, executionType: TriggerExecutionType): List<AutomationTrigger.Event> {
        return fullSchedule.triggers.filter {
            when(executionType) {
                TriggerExecutionType.EXECUTION -> !it.isCancellation
                TriggerExecutionType.DELAY_CANCELLATION -> it.isCancellation
            }
        }.mapNotNull {
            val type = convertLegacyType(it.triggerType) ?: return@mapNotNull null
            AutomationTrigger.Event(
                EventAutomationTrigger(
                    id = AutomationTrigger.generateStableId(type.value, it.goal, it.jsonPredicate, executionType),
                    goal = it.goal,
                    type = type,
                    predicate = it.jsonPredicate
                )
            )
        }

    }

    private fun convertLegacyType(legacyType: Int): EventAutomationTriggerType? {
        return when (legacyType) {
            1 -> EventAutomationTriggerType.FOREGROUND
            2 -> EventAutomationTriggerType.BACKGROUND
            3 -> EventAutomationTriggerType.REGION_ENTER
            4 -> EventAutomationTriggerType.REGION_EXIT
            5 -> EventAutomationTriggerType.CUSTOM_EVENT_COUNT
            6 -> EventAutomationTriggerType.CUSTOM_EVENT_VALUE
            7 -> EventAutomationTriggerType.SCREEN
            8 -> EventAutomationTriggerType.APP_INIT
            9 -> EventAutomationTriggerType.ACTIVE_SESSION
            10 -> EventAutomationTriggerType.VERSION
            11 -> EventAutomationTriggerType.FEATURE_FLAG_INTERACTION
            else -> null
        }
    }

    private fun getTriggeringInfo(schedule: ScheduleEntity): TriggeringInfo {
        return TriggeringInfo(
            context = null,
            date = schedule.triggeredTime
        )
    }

    private fun getPreparedScheduleInfo(schedule: ScheduleEntity): PreparedScheduleInfo? {
        if (schedule.executionState == ScheduleState.PREPARING_SCHEDULE ||
            schedule.executionState == ScheduleState.EXECUTING) {
            return PreparedScheduleInfo(
                scheduleId = schedule.scheduleId,
                productId = schedule.productId,
                campaigns = schedule.campaigns,
                contactId = null,
                experimentResult = null,
                reportingContext = schedule.reportingContext
            )
        }
        return null
    }

    private fun convertScheduleState(scheduleState: Int): AutomationScheduleState {
        return when (scheduleState) {
            ScheduleState.IDLE -> AutomationScheduleState.IDLE
            ScheduleState.PREPARING_SCHEDULE -> AutomationScheduleState.PREPARED
            ScheduleState.WAITING_SCHEDULE_CONDITIONS -> AutomationScheduleState.PREPARED
            ScheduleState.TIME_DELAYED -> AutomationScheduleState.PREPARED
            ScheduleState.EXECUTING -> AutomationScheduleState.EXECUTING
            ScheduleState.PAUSED -> AutomationScheduleState.PAUSED
            ScheduleState.FINISHED -> AutomationScheduleState.FINISHED
            else -> AutomationScheduleState.FINISHED
        }
    }

    private fun convertTriggers(triggers: List<TriggerEntity>): List<TriggerData> {
        return triggers.mapNotNull {
            val type = convertLegacyType(it.triggerType) ?: return@mapNotNull null

            val executionType = if (it.isCancellation) {
                TriggerExecutionType.DELAY_CANCELLATION
            } else {
                TriggerExecutionType.EXECUTION
            }

            TriggerData(
                scheduleID = it.parentScheduleId,
                triggerID = AutomationTrigger.generateStableId(type.value, it.goal, it.jsonPredicate, executionType),
                triggerCount = it.progress,
                children = emptyMap(),
                lastTriggerableState = null
            )
        }
    }

    data class Converted(
        val scheduleData: AutomationScheduleData,
        val triggerData: List<TriggerData>
    )
}

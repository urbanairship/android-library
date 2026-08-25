package com.urbanairship.automation.storage

import com.urbanairship.UALog
import com.urbanairship.automation.AutomationAppState
import com.urbanairship.automation.AutomationAudience
import com.urbanairship.automation.AutomationDelay
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.AutomationTrigger
import com.urbanairship.automation.EventAutomationTrigger
import com.urbanairship.automation.EventAutomationTriggerType
import com.urbanairship.automation.engine.AutomationScheduleData
import com.urbanairship.automation.engine.AutomationScheduleState
import com.urbanairship.automation.engine.AutomationStoreInterface
import com.urbanairship.automation.engine.PreparedScheduleInfo
import com.urbanairship.automation.engine.TriggeringInfo
import com.urbanairship.automation.engine.triggerprocessor.TriggerData
import com.urbanairship.automation.engine.triggerprocessor.TriggerExecutionType
import com.urbanairship.automation.limits.LedgerEvent
import com.urbanairship.automation.limits.LedgerExecutionResult
import com.urbanairship.automation.limits.LedgerStoreInterface
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.preferences.AsyncPrefKey
import com.urbanairship.preferences.PreferenceStore
import com.urbanairship.util.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

internal class AutomationStoreMigrator(
    private val legacyDatabase: AutomationDatabase,
    private val store: AutomationStoreInterface,
    private val ledgerStore: LedgerStoreInterface,
    private val dataStore: PreferenceStore,
    private val clock: Clock = Clock.DEFAULT_CLOCK
) {

    internal suspend fun migrateData() {
        migrateLegacyStore()
        backfillCurrentStoreIfNeeded()
    }

    /**
     * Pre-rewrite (Java) store migration: moves any legacy schedules into the
     * current store. Their execution counts reach the ledger through
     * [backfillCurrentStoreIfNeeded], which runs straight after and sees the
     * schedules this pass just moved.
     */
    private suspend fun migrateLegacyStore() {
        val legacyDao = legacyDatabase.scheduleDao

        val oldSchedules = legacyDao.getSchedules()
        if (oldSchedules.isEmpty()) return

        val converted = convert(oldSchedules)
        if (converted.isNotEmpty()) {
            val byId = converted.associateBy { it.scheduleData.schedule.identifier }
            val ids = byId.keys.toList()

            // Schedules already present in the new store indicate a prior
            // migration whose legacy cleanup failed. Re-running the upsert
            // would clobber whatever state they have since accumulated.
            val shouldMigrate = store.getSchedules(ids).isEmpty()

            if (shouldMigrate) {
                store.upsertSchedules(ids) { id, _ ->
                    requireNotNull(byId[id]).scheduleData
                }
                store.upsertTriggers(converted.flatMap { it.triggerData })
            }
        }
        legacyDao.deleteSchedules(oldSchedules)
    }

    /**
     * Post-rewrite (Kotlin) store backfill: on the first launch under the
     * ledger, records execution counts already held in the current store so
     * limits upgraded from a pre-ledger SDK are not reset. This is the only
     * backfill pass, so it covers both schedules that were always in the
     * current store and any [migrateLegacyStore] has just moved into it.
     *
     * A failure leaves the completion flag unset and the counts in the store,
     * so the next launch retries. That is why the legacy pass does not record
     * its own backfill: it swallowed record failures but still marked the work
     * done, losing those counts for good.
     *
     * Runs at most once per app: the completion flag is persisted, so later
     * launches — where the current store's counts also include ledger-recorded
     * executions — never double-count. It runs during migration, before the
     * engine executes any schedule, so the counts captured here are purely
     * pre-ledger.
     */
    private suspend fun backfillCurrentStoreIfNeeded() {
        try {
            if (isLedgerBackfillCompleted()) return

            val schedules = store.getSchedules()
            val events = backfillLedgerEvents(schedules, clock.now())
            if (events.isNotEmpty()) {
                ledgerStore.recordEvents(events)
            }
            markLedgerBackfillCompleted()
        } catch (ex: Exception) {
            // Leave the flag unset so the backfill is retried on the next launch.
            UALog.e(ex) { "Failed to backfill current store execution counts into ledger" }
        }
    }

    private suspend fun isLedgerBackfillCompleted(): Boolean =
        dataStore.get(LEDGER_BACKFILL_COMPLETED_KEY) == true

    private suspend fun markLedgerBackfillCompleted() {
        dataStore.put(LEDGER_BACKFILL_COMPLETED_KEY, true)
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
                        if (it >= 0) { Instant.ofEpochMilli(it) } else { null }
                    },
                    endDate = fullSchedule.schedule.scheduleEnd.let {
                        if (it >= 0) { Instant.ofEpochMilli(it) } else { null }
                    },
                    created = Instant.ofEpochMilli(fullSchedule.schedule.newUserEvaluationDate),
                    group = fullSchedule.schedule.group,
                    priority = fullSchedule.schedule.priority,
                    limit = fullSchedule.schedule.limit.let {
                        if (it >= 0) { it.toUInt() } else { null }
                    },
                    interval = fullSchedule.schedule.interval.milliseconds,
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
                        Instant.ofEpochMilli(fullSchedule.schedule.executionStateChangeDate),
                        fullSchedule.schedule.count,
                        getTriggeringInfo(fullSchedule.schedule),
                        getPreparedScheduleInfo(fullSchedule.schedule),
                        triggerSessionId = UUID.randomUUID().toString()
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
            screens = fullSchedule.schedule.screens?.ifEmpty { null },
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
            date = Instant.ofEpochMilli(schedule.triggeredTime)
        )
    }

    private fun getPreparedScheduleInfo(schedule: ScheduleEntity, audienceCheck: Boolean = true): PreparedScheduleInfo? {
        val state = ScheduleState.fromValue(schedule.executionState) ?: return null

        return when(state) {
            ScheduleState.PREPARING_SCHEDULE, ScheduleState.EXECUTING -> {
                PreparedScheduleInfo(
                    scheduleId = schedule.scheduleId,
                    productId = schedule.productId,
                    campaigns = schedule.campaigns,
                    contactId = null,
                    experimentResult = null,
                    reportingContext = schedule.reportingContext,
                    triggerSessionId = UUID.randomUUID().toString(),
                    additionalAudienceCheckResult = audienceCheck,
                    priority = schedule.priority
                )
            }
            else -> null
        }
    }

    private fun convertScheduleState(scheduleState: Int): AutomationScheduleState {
        val state = ScheduleState.fromValue(scheduleState)
            ?: return AutomationScheduleState.FINISHED

        return when (state) {
            ScheduleState.IDLE -> AutomationScheduleState.IDLE
            ScheduleState.PREPARING_SCHEDULE -> AutomationScheduleState.PREPARED
            ScheduleState.WAITING_SCHEDULE_CONDITIONS -> AutomationScheduleState.PREPARED
            ScheduleState.TIME_DELAYED -> AutomationScheduleState.PREPARED
            ScheduleState.EXECUTING -> AutomationScheduleState.EXECUTING
            ScheduleState.PAUSED -> AutomationScheduleState.PAUSED
            ScheduleState.FINISHED -> AutomationScheduleState.FINISHED
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
                scheduleId = it.parentScheduleId,
                triggerId = AutomationTrigger.generateStableId(type.value, it.goal, it.jsonPredicate, executionType),
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

    internal companion object {

        private val LEDGER_BACKFILL_COMPLETED_KEY: AsyncPrefKey<Boolean> =
            AsyncPrefKey.boolean("com.urbanairship.automation.ledger.backfillCompleted")

        /**
         * Builds the backfill ledger events for a set of migrating schedules.
         *
         * Each schedule with a non-zero execution count contributes a single
         * `execution` event with `result: backfill` and `count` equal to that
         * legacy count. Backfill events carry no `trigger_id` and are never
         * recorded under a `shared_id`, so pre-ledger history stays scoped to
         * the schedule and never pollutes a pooled group tally. The timestamp
         * is the migration time — an upper bound on when those executions
         * actually happened.
         */
        internal fun backfillLedgerEvents(
            schedules: List<AutomationScheduleData>,
            timestamp: Instant
        ): List<LedgerEvent> {
            return schedules.mapNotNull { data ->
                val count = data.executionCount
                if (count <= 0) return@mapNotNull null

                LedgerEvent.Execution(
                    scheduleId = data.schedule.identifier,
                    sharedId = null,
                    triggerId = null,
                    timestamp = timestamp,
                    count = count,
                    result = LedgerExecutionResult.BACKFILL,
                    cancel = null
                )
            }
        }
    }
}

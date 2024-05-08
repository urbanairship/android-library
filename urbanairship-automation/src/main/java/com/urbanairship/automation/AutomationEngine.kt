package com.urbanairship.automation

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import com.urbanairship.automation.engine.AutomationDelayProcessorInterface
import com.urbanairship.automation.engine.AutomationPreparer
import com.urbanairship.automation.engine.AutomationScheduleState
import com.urbanairship.automation.engine.PreparedSchedule
import com.urbanairship.automation.engine.SchedulePrepareResult
import com.urbanairship.automation.engine.triggerprocessor.AutomationTriggerProcessor
import com.urbanairship.automation.engine.triggerprocessor.TriggerExecutionType
import com.urbanairship.automation.engine.triggerprocessor.TriggerResult
import com.urbanairship.automation.utils.ScheduleConditionsChangedNotifier
import com.urbanairship.automation.utils.TaskSleeper
import com.urbanairship.util.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.annotations.VisibleForTesting

internal interface AutomationEngineInterface {
    fun setEnginePaused(paused: Boolean)
    fun setExecutionPaused(paused: Boolean)
    fun start()

    suspend fun upsertSchedules(schedules: List<AutomationSchedule>)
    suspend fun stopSchedules(identifiers: List<String>)
    suspend fun cancelSchedules(identifiers: List<String>)
    suspend fun cancelSchedules(group: String)
    suspend fun cancelSchedulesWith(type: AutomationSchedule.ScheduleType)
    suspend fun getSchedules(): List<AutomationSchedule>
    suspend fun getSchedule(identifier: String): AutomationSchedule?
    suspend fun getSchedules(group: String): List<AutomationSchedule>
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class AutomationEngine(
    private val context: Context,
    private val store: ScheduleStoreInterface,
    private val executor: AutomationExecutorInterface,
    private val preparer: AutomationPreparer,
    private val scheduleConditionsChangedNotifier: ScheduleConditionsChangedNotifier,
    private val eventsFeed: AutomationEventFeed,
    private val triggerProcessor: AutomationTriggerProcessor,
    private val delayProcessor: AutomationDelayProcessorInterface,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    private val sleeper: TaskSleeper = TaskSleeper.default,
    private val dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher(),
) : AutomationEngineInterface {

    private companion object {
        val INTERRUPTIBLE_STATES = listOf(
            AutomationScheduleState.EXECUTING,
            AutomationScheduleState.PREPARED,
            AutomationScheduleState.TRIGGERED
        )
    }
    enum class ScheduleRestoreState {
        IDLE,
        IN_PROGRESS,
        RESTORED
    }

    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(dispatcher + supervisorJob)

    private var isPaused = MutableStateFlow(false)
    private var isExecutionPaused = MutableStateFlow(false)
    private var restoreState = MutableStateFlow(ScheduleRestoreState.IDLE)

    @VisibleForTesting
    internal fun isStarted(): Boolean = restoreState.value != ScheduleRestoreState.IDLE

    @VisibleForTesting
    internal fun isPaused(): Boolean = isPaused.value

    @VisibleForTesting
    internal fun isExecutionPaused(): Boolean = isExecutionPaused.value

    override fun setEnginePaused(paused: Boolean) {
        isPaused.update { paused }
    }

    override fun setExecutionPaused(paused: Boolean) {
        isExecutionPaused.update { paused }
    }

    override fun start() {
        restoreState.value = ScheduleRestoreState.IN_PROGRESS

        scope.launch {
            restoreSchedules()
            restoreState.value = ScheduleRestoreState.RESTORED

            if (!isActive) { return@launch }

            launch {
                triggerProcessor.getTriggerResults().collect {
                    if (isActive) {
                        processTriggerResult(it)
                    }
                }
            }

            launch {
                eventsFeed.feed.collect {
                    if (isActive) {
                        triggerProcessor.processEvent(it)
                    }
                }
            }
        }
    }

    fun stop() {
        restoreState.value = ScheduleRestoreState.IDLE
        supervisorJob.cancelChildren()
    }

    private suspend fun waitForScheduleRestore() {
        restoreState.first { it == ScheduleRestoreState.RESTORED }
    }

    override suspend fun stopSchedules(identifiers: List<String>) = withContext(dispatcher) {
        waitForScheduleRestore()

        UALog.d { "Stopping schedules $identifiers" }

        val timestamp = clock.currentTimeMillis()
        for (item in identifiers) {
            updateState(item) { data ->
                data.setSchedule(data.schedule.copyWith(endDate = timestamp.toULong()))
                data.finished(timestamp)
            }
        }
    }

    override suspend fun upsertSchedules(schedules: List<AutomationSchedule>) = withContext(dispatcher) {
        waitForScheduleRestore()

        val idToSchedule = schedules.associateBy { it.identifier }

        UALog.d { "Upsert schedules ${idToSchedule.keys}" }
        val data = store.upsertSchedules(idToSchedule.keys.toList()) { identifier, data ->
            val schedule = requireNotNull(idToSchedule[identifier])
            val stored = schedule.updateOrCreate(data, clock.currentTimeMillis())
            stored.updateState(clock.currentTimeMillis())
        }

        triggerProcessor.updateSchedules(data)
    }

    override suspend fun cancelSchedules(identifiers: List<String>) = withContext(dispatcher) {
        waitForScheduleRestore()

        UALog.d { "Cancelling schedules ${identifiers})" }

        store.deleteSchedules(identifiers)
        triggerProcessor.cancel(identifiers)
    }

    override suspend fun cancelSchedules(group: String) = withContext(dispatcher) {
        waitForScheduleRestore()

        UALog.d { "Cancelling schedules with group ${group})" }

        store.deleteSchedules(group)
        triggerProcessor.cancel(group)
    }

    override suspend fun cancelSchedulesWith(type: AutomationSchedule.ScheduleType) = withContext(dispatcher) {
        waitForScheduleRestore()

        UALog.d { "Cancelling schedules with type ${type})" }

        //we don't store schedule type as a separate field, but it's a part of airship json, so we
        // can't utilize room to filter out our results
        val ids = getSchedules().mapNotNull { schedule ->
            when (schedule.data) {
                is AutomationSchedule.ScheduleData.Actions -> {
                    if (type != AutomationSchedule.ScheduleType.ACTIONS) {
                        return@mapNotNull null
                    }
                    return@mapNotNull schedule.identifier
                }

                is AutomationSchedule.ScheduleData.Deferred -> {
                    if (type != AutomationSchedule.ScheduleType.DEFERRED) {
                        return@mapNotNull null
                    }
                    return@mapNotNull schedule.identifier
                }

                is AutomationSchedule.ScheduleData.InAppMessageData -> {
                    if (type != AutomationSchedule.ScheduleType.IN_APP_MESSAGE) {
                        return@mapNotNull null
                    }
                    return@mapNotNull schedule.identifier
                }
            }
        }

        store.deleteSchedules(ids)
        triggerProcessor.cancel(ids)
    }

    override suspend fun getSchedules(): List<AutomationSchedule> = withContext(dispatcher) {
        return@withContext store
            .getSchedules()
            .filter { !it.shouldDelete(clock.currentTimeMillis()) }
            .map { it.schedule }
    }

    override suspend fun getSchedule(identifier: String): AutomationSchedule? = withContext(dispatcher) {
        val result = store.getSchedule(identifier) ?: return@withContext null
        if (result.isExpired(clock.currentTimeMillis())) {
            return@withContext null
        }

        return@withContext result.schedule
    }

    override suspend fun getSchedules(group: String): List<AutomationSchedule> = withContext(dispatcher) {
        val date = clock.currentTimeMillis()

        return@withContext store
            .getSchedules(group)
            .filter { !it.isExpired(date) }
            .map { it.schedule }
            .toList()
    }

    private suspend fun updateState(
        identifier: String,
        updateBlock: (AutomationScheduleData) -> AutomationScheduleData
    ): AutomationScheduleData? {
        val result = store.updateSchedule(identifier, updateBlock) ?: return null
        triggerProcessor.updateScheduleState(identifier, result.scheduleState)
        return result
    }

    // Runs queued, via start() coroutine
    private suspend fun processTriggerResult(result: TriggerResult) {
        val date = clock.currentTimeMillis()

        try {
            when(result.triggerExecutionType) {
                TriggerExecutionType.DELAY_CANCELLATION -> {
                    val data = updateState(result.scheduleId) { it.executionCancelled(date) }
                    data?.let { preparer.cancelled(it.schedule) }
                }
                TriggerExecutionType.EXECUTION -> {
                    updateState(result.scheduleId) { it.triggered(result.triggerInfo.context, date)}
                    startTaskToProcessTriggeredSchedule(result.scheduleId)
                }
            }
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to process trigger result $result" }
        }
    }

    // Runs queued, via start() coroutine
    private suspend fun restoreSchedules() {
        val now = clock.currentTimeMillis()

        val schedules = store
            .getSchedules()
            .sortedWith(AutomationScheduleData.Comparator(now))

        // Restore triggers
        triggerProcessor.restoreSchedules(schedules)

        // Handle interrupted
        schedules.filter {
            it.isInState(INTERRUPTIBLE_STATES)
        }.forEach { data ->
            val updated: AutomationScheduleData?
            val preparedInfo = data.preparedScheduleInfo
            if (data.scheduleState == AutomationScheduleState.EXECUTING && preparedInfo != null) {
                val behavior = executor.interrupted(data.schedule, preparedInfo)
                updated = updateState(data.schedule.identifier) {
                    it.executionInterrupted(now, retry = behavior == InterruptedBehavior.RETRY)
                }
                if (updated?.scheduleState == AutomationScheduleState.PAUSED) {
                    handleInterval(updated.schedule.interval?.toLong() ?: 0L, data.schedule.identifier)
                }
            } else {
                updated = updateState(data.schedule.identifier) { it.prepareInterrupted(now) }
            }

            if (updated?.scheduleState == AutomationScheduleState.TRIGGERED) {
                startTaskToProcessTriggeredSchedule(updated.schedule.identifier)
            }
        }

        // Restore Intervals
        schedules
            .filter { it.scheduleState == AutomationScheduleState.PAUSED }
            .forEach { data ->
                val interval = data.schedule.interval?.toLong() ?: 0
                val remaining = interval - clock.currentTimeMillis()
                handleInterval(remaining, data.schedule.identifier)
            }

        // Delete finished schedules
        val toDelete = schedules
            .filter { it.shouldDelete(now) }
            .map { it.schedule.identifier }

        if (toDelete.isNotEmpty()) {
            store.deleteSchedules(toDelete)
            triggerProcessor.cancel(toDelete)
        }
    }

    private suspend fun startTaskToProcessTriggeredSchedule(scheduleID: String) {
        scope.launch {
            UALog.v { "Processing triggered schedule $scheduleID" }
            processTriggeredSchedule(scheduleID)
        }
        // Give the task above a chance to run
        yield()
    }

    private suspend fun processTriggeredSchedule(scheduleID: String) {
        val data = store.getSchedule(scheduleID)
        if (data == null) {
            UALog.v { "Aborting processing schedule $scheduleID, no longer in database." }
            return
        }

        if (!data.isInState(listOf(AutomationScheduleState.TRIGGERED))) {
            UALog.v { "Aborting processing schedule $data, no longer triggered." }
            return
        }

        if (!data.isActive(clock.currentTimeMillis())) {
            UALog.v { "Aborting processing schedule $data, no longer active." }
            preparer.cancelled(data.schedule)
            return
        }

        val (scheduleData, preparedSchedule) = prepareSchedule(data) ?: return
        executeSchedule(scheduleData, preparedSchedule)
    }

    private suspend fun prepareSchedule(data: AutomationScheduleData): Pair<AutomationScheduleData, PreparedSchedule>? {
        UALog.v { "Preparing schedule $data" }

        val result = preparer.prepare(context, data.schedule, data.triggerInfo?.context)
        UALog.v { "Preparing schedule $data result: $result" }

        val updated = updateState(data.schedule.identifier) {
            if (!it.isInState(listOf(AutomationScheduleState.TRIGGERED))) {
                return@updateState it
            }

            return@updateState when(result) {
                is SchedulePrepareResult.Prepared -> {
                    it.prepared(result.schedule.info, clock.currentTimeMillis())
                }
                SchedulePrepareResult.Penalize -> {
                    it.prepareCancelled(clock.currentTimeMillis(), penalize = true)
                }
                SchedulePrepareResult.Skip -> {
                    it.prepareCancelled(clock.currentTimeMillis(), penalize = false)
                }
                else -> { it }
            }
        } ?: data

        return when(result) {
            SchedulePrepareResult.Cancel -> {
                store.deleteSchedules(listOf(data.schedule.identifier))
                null
            }
            is SchedulePrepareResult.Prepared -> {
                Pair(updated, result.schedule)
            }
            SchedulePrepareResult.Skip -> {
                null
            }
            SchedulePrepareResult.Penalize -> {
                null
            }
            SchedulePrepareResult.Invalidate -> {
                startTaskToProcessTriggeredSchedule(data.schedule.identifier)
                null
            }
        }
    }

    private suspend fun executeSchedule(data: AutomationScheduleData, preparedSchedule: PreparedSchedule) {
        withContext(Dispatchers.Main) {
            UALog.v { "Starting to execute schedule $data" }

            val scheduleID = data.schedule.identifier
            while (true) {
                when (checkReady(data, preparedSchedule)) {
                    ScheduleReadyResult.READY -> {}
                    ScheduleReadyResult.INVALIDATE -> {
                        val updated = updateState(scheduleID) { it.executionInvalidated(clock.currentTimeMillis())}
                        if (updated?.scheduleState == AutomationScheduleState.TRIGGERED) {
                            startTaskToProcessTriggeredSchedule(scheduleID)
                        } else {
                            preparer.cancelled(data.schedule)
                        }
                        return@withContext
                    }
                    ScheduleReadyResult.NOT_READY -> {
                        scheduleConditionsChangedNotifier.wait()
                        continue
                    }
                    ScheduleReadyResult.SKIP -> {
                        updateState(scheduleID) { it.executionSkipped(clock.currentTimeMillis()) }
                        preparer.cancelled(data.schedule)
                        return@withContext
                    }
                }

                UALog.v { "Executing schedule ${preparedSchedule.info.scheduleID}" }

                val updateStateJob = async(dispatcher) {
                    updateState(preparedSchedule.info.scheduleID) { it.executing(clock.currentTimeMillis()) }
                }

                val result = executor.execute(preparedSchedule)

                updateStateJob.join()

                UALog.v { "Executing result ${preparedSchedule.info.scheduleID} $result" }

                when(result) {
                    ScheduleExecuteResult.CANCEL -> {
                        store.deleteSchedules(listOf(scheduleID))
                        triggerProcessor.cancel(listOf(scheduleID))
                    }
                    ScheduleExecuteResult.FINISHED -> {
                        val update = updateState(scheduleID) { it.finishedExecuting(clock.currentTimeMillis()) }
                        if (update?.scheduleState == AutomationScheduleState.PAUSED) {
                            val interval = update.schedule.interval?.toLong() ?: 0L
                            handleInterval(interval, scheduleID)
                        }
                    }
                    ScheduleExecuteResult.RETRY -> continue
                }

                return@withContext
            }
        }
    }

    private suspend fun checkReady(data: AutomationScheduleData, preparedSchedule: PreparedSchedule): ScheduleReadyResult {
        UALog.v { "Checking if schedule is ready $data" }

        val triggerDate = data.triggerInfo?.date ?: data.scheduleStateChangeDate
        delayProcessor.process(data.schedule.delay, triggerDate)

        UALog.v { "Delay conditions met $data" }

        // Make sure we are still up to date. Data might change due to a change
        // in the data, schedule was cancelled, or if a delay cancellation trigger
        // was fired.
        val stored = store.getSchedule(data.schedule.identifier)
        if (stored?.scheduleState != AutomationScheduleState.PREPARED || stored.schedule != data.schedule) {
            UALog.v { "Schedule no longer valid, invalidating $data" }
            return ScheduleReadyResult.INVALIDATE
        }

        val precheckResult = executor.isReadyPrecheck(data.schedule)
        if (precheckResult != ScheduleReadyResult.READY) {
            UALog.v { "Precheck not ready $stored" }
            return precheckResult
        }

        // Verify conditions still met
        if (!delayProcessor.areConditionsMet(stored.schedule.delay)) {
            UALog.v { "Delay conditions not met, not ready $stored" }
            return ScheduleReadyResult.NOT_READY
        }

        if (isExecutionPaused.value || isPaused.value) {
            UALog.v { "Executor paused, not ready $stored" }
            return ScheduleReadyResult.NOT_READY
        }

        if (!data.isActive(clock.currentTimeMillis())) {
            UALog.v { "Schedule no longer active, Invalidating $stored" }
            return ScheduleReadyResult.INVALIDATE
        }

        val result = executor.isReady(preparedSchedule)
        if (result != ScheduleReadyResult.READY) {
            UALog.v { "Schedule not ready $stored" }
        }

        return result
    }

    private fun handleInterval(interval: Long, scheduleID: String) {
        scope.launch {
            sleeper.sleep(interval.seconds)
            updateState(scheduleID) {
                it.idle(clock.currentTimeMillis())
            }
        }
    }
}

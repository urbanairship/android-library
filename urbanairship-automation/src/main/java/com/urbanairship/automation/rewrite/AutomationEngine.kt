package com.urbanairship.automation.rewrite

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import com.urbanairship.automation.rewrite.engine.AutomationDelayProcessorInterface
import com.urbanairship.automation.rewrite.engine.AutomationPreparerInterface
import com.urbanairship.automation.rewrite.engine.AutomationScheduleState
import com.urbanairship.automation.rewrite.engine.PreparedSchedule
import com.urbanairship.automation.rewrite.engine.SchedulePrepareResult
import com.urbanairship.automation.rewrite.engine.triggerprocessor.AutomationTriggerProcessorInterface
import com.urbanairship.automation.rewrite.engine.triggerprocessor.TriggerExecutionType
import com.urbanairship.automation.rewrite.engine.triggerprocessor.TriggerResult
import com.urbanairship.automation.rewrite.utils.ScheduleConditionsChangedNotifier
import com.urbanairship.automation.rewrite.utils.TaskSleeper
import com.urbanairship.util.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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

//TODO: check multithreading and processing threads
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class AutomationEngine(
    private val context: Context,
    private val store: AutomationStore,
    private val executor: AutomationExecutorInterface,
    private val preparer: AutomationPreparerInterface,
    private val scheduleConditionsChangedNotifier: ScheduleConditionsChangedNotifier,
    private val eventsFeed: AutomationEventFeed,
    private val triggerProcessor: AutomationTriggerProcessorInterface,
    private val delayProcessor: AutomationDelayProcessorInterface,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    private val sleeper: TaskSleeper = TaskSleeper.default,
    private val processingDispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher(),
    private val secondaryDispatcher: CoroutineDispatcher = AirshipDispatchers.IO
) : AutomationEngineInterface {

    private val processingScope = CoroutineScope(processingDispatcher + SupervisorJob())
    private val secondaryScope = CoroutineScope(secondaryDispatcher + SupervisorJob())

    private var isPaused = MutableStateFlow(false)
    private var isExecutionPaused = MutableStateFlow(false)
    private var startTask: Deferred<Unit>? = null

    override fun setEnginePaused(paused: Boolean) {
        isPaused.update { paused }
    }

    override fun setExecutionPaused(paused: Boolean) {
        isExecutionPaused.update { paused }
    }

    override fun start() {
        startTask = processingScope.async { restoreSchedules() }

        processingScope.launch {
            triggerProcessor.getTriggerResults().collect {
                yield()
                processTriggerResult(it)
            }
        }

        processingScope.launch {
            eventsFeed.feed.collect {
                yield()
                processingScope.launch { triggerProcessor.processEvent(it) }
            }
        }
    }

    public fun stop() {
        startTask?.cancel()
        processingScope.cancel()
    }

    override suspend fun stopSchedules(identifiers: List<String>) {
        UALog.d { "Stopping schedules $identifiers" }
        startTask?.await()
        val timestamp = clock.currentTimeMillis()
        for (item in identifiers) {
            updateState(item) { data ->
                data.setSchedule(data.schedule.copyWith(endDate = timestamp.toULong()))
                data.finished(timestamp)
            }
        }
    }

    override suspend fun upsertSchedules(schedules: List<AutomationSchedule>) {
        startTask?.await()

        val idToSchedule = schedules.associateBy { it.identifier }

        UALog.d { "Upserting schedules ${idToSchedule.keys}" }
        val data = store.upsertSchedules(idToSchedule.keys.toList()) { identifier, data ->
            val schedule = idToSchedule[identifier]
                ?: throw IllegalArgumentException("Invalid schedule identifier")

            val stored = schedule.updateOrCreate(data, clock.currentTimeMillis())
            stored.updateState(clock.currentTimeMillis())
        }

        triggerProcessor.updateSchedules(data)
    }

    override suspend fun cancelSchedules(identifiers: List<String>) {
        UALog.d { "Cancelling schedules ${identifiers})" }
        startTask?.await()

        store.deleteSchedules(identifiers)
        triggerProcessor.cancel(identifiers)
    }

    override suspend fun cancelSchedules(group: String) {
        UALog.d { "Cancelling schedules with group ${group})" }
        startTask?.await()
        store.deleteSchedules(group)
        triggerProcessor.cancel(group)
    }

    override suspend fun cancelSchedulesWith(type: AutomationSchedule.ScheduleType) {
        UALog.d { "Cancelling schedules with type ${type})" }

        startTask?.await()

        //we don't store schedule type as a separate field, but it's a part of airship json, so we
        // can't utilize room to filter out our results
        val ids = getSchedules().mapNotNull { schedule ->
            when(schedule.data) {
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

    override suspend fun getSchedules(): List<AutomationSchedule> {
        return store
            .getSchedules()
            .filter { !it.shouldDelete(clock.currentTimeMillis()) }
            .map { it.schedule }
    }

    override suspend fun getSchedule(identifier: String): AutomationSchedule? {
        val result = store.getSchedule(identifier) ?: return null
        if (result.isExpired(clock.currentTimeMillis())) {
            return null
        }

        return result.schedule
    }

    override suspend fun getSchedules(group: String): List<AutomationSchedule> {
        val date = clock.currentTimeMillis()

        return store
            .getSchedules(group)
            .filter { !it.isExpired(date) }
            .map { it.schedule }
            .toList()
    }

    @VisibleForTesting
    internal fun isStarted(): Boolean = startTask != null && !(startTask?.isCancelled ?: true)

    internal fun isPaused(): Boolean = isPaused.value
    internal fun isExecutionPaused(): Boolean = isExecutionPaused.value

    private suspend fun updateState(
        identifier: String,
        updateBlock: (AutomationScheduleData) -> AutomationScheduleData): AutomationScheduleData? {

        val result = store.updateSchedule(identifier, updateBlock) ?: return null
        triggerProcessor.updateScheduleState(identifier, result.scheduleState)
        return result
    }

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

    private suspend fun restoreSchedules() {
        val now = clock.currentTimeMillis()

        val schedules = store
            .getSchedules()
            .sortedWith(AutomationScheduleData.Comparator(now))

        // Restore triggers
        triggerProcessor.restoreSchedules(schedules)

        // Handle interrupted
        schedules.filter {
            it.isInState(listOf(
                AutomationScheduleState.EXECUTING,
                AutomationScheduleState.PREPARED,
                AutomationScheduleState.TRIGGERED)) }
            .forEach { data ->
                val preparedInfo = data.preparedScheduleInfo
                if (data.scheduleState == AutomationScheduleState.EXECUTING && preparedInfo != null) {
                    val behavior = executor.interrupted(data.schedule, preparedInfo)
                    updateState(data.schedule.identifier) {
                        it.executionInterrupted(now, retry = behavior == InterruptedBehavior.RETRY)
                    }
                } else {
                    updateState(data.schedule.identifier) { it.prepareInterrupted(now) }
                }

                if (data.scheduleState == AutomationScheduleState.TRIGGERED) {
                    startTaskToProcessTriggeredSchedule(data.schedule.identifier)
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
        try {
            UALog.v { "Processing triggered schedule $scheduleID" }
            processTriggeredSchedule(scheduleID)
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to process triggered schedule $scheduleID" }
        }
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

        val prepared = prepareSchedule(data) ?: return
        startExecuting(data, prepared)

    }

    private suspend fun prepareSchedule(data: AutomationScheduleData): PreparedSchedule? {
        UALog.v { "Preparing schedule $data" }

        val result = preparer.prepare(context, data.schedule, data.triggerInfo?.context)
        UALog.v { "Preparing schedule $data result: $result" }

        return when(result) {
            SchedulePrepareResult.Cancel -> {
                store.deleteSchedules(listOf(data.schedule.identifier))
                null
            }
            is SchedulePrepareResult.Prepared -> {
                if (data.isInState(listOf(AutomationScheduleState.TRIGGERED))) {
                    data.prepared(result.schedule.info, clock.currentTimeMillis())
                }

                result.schedule
            }
            SchedulePrepareResult.Skip -> {
                if (data.isInState(listOf(AutomationScheduleState.TRIGGERED))) {
                    data.prepareCancelled(clock.currentTimeMillis(), penalize = false)
                }
                null
            }
            SchedulePrepareResult.Penalize -> {
                if (data.isInState(listOf(AutomationScheduleState.TRIGGERED))) {
                    data.prepareCancelled(clock.currentTimeMillis(), penalize = true)
                }
                null
            }
            SchedulePrepareResult.Invalidate -> {
                startTaskToProcessTriggeredSchedule(data.schedule.identifier)
                null
            }
        }
    }

    private suspend fun startExecuting(data: AutomationScheduleData, preparedSchedule: PreparedSchedule) {
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
                        }
                        return@withContext
                    }
                    ScheduleReadyResult.NOT_READY -> {
                        scheduleConditionsChangedNotifier.wait()
                        continue
                    }
                    ScheduleReadyResult.SKIP -> {
                        updateState(scheduleID) { it.executionSkipped(clock.currentTimeMillis()) }
                        return@withContext
                    }
                }

                when(execute(preparedSchedule)) {
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

    private suspend fun execute(preparedSchedule: PreparedSchedule): ScheduleExecuteResult {
        UALog.v { "Executing schedule ${preparedSchedule.info.scheduleID}" }
        val job = secondaryScope.async {
            updateState(preparedSchedule.info.scheduleID) {it.executing(clock.currentTimeMillis())}
        }

        val result = executor.execute(preparedSchedule)
        job.await()

        UALog.v { "Executing result ${preparedSchedule.info.scheduleID} $result" }
        return result
    }

    private fun handleInterval(interval: Long, scheduleID: String) {
        secondaryScope.launch {
            sleeper.sleep(interval.seconds)
            updateState(scheduleID) {
                it.idle(clock.currentTimeMillis())
            }
        }
    }
}

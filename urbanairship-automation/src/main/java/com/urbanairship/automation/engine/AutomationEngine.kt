/* Copyright Airship and Contributors */

package com.urbanairship.automation.engine

import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.engine.triggerprocessor.AutomationTriggerProcessor
import com.urbanairship.automation.engine.triggerprocessor.TriggerExecutionType
import com.urbanairship.automation.engine.triggerprocessor.TriggerResult
import com.urbanairship.automation.storage.AutomationStoreMigrator
import com.urbanairship.automation.updateOrCreate
import com.urbanairship.automation.utils.ScheduleConditionsChangedNotifier
import com.urbanairship.util.Clock
import com.urbanairship.util.TaskSleeper
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class AutomationEngine(
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
    private val automationStoreMigrator: AutomationStoreMigrator
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
    private var pendingExecution = MutableStateFlow(setOf<PreparedData>())

    private var preprocessingDelayJobs = mutableListOf<Job>()


    @VisibleForTesting
    internal fun isStarted(): Boolean = restoreState.value != ScheduleRestoreState.IDLE

    @VisibleForTesting
    internal fun isPaused(): Boolean = isPaused.value

    @VisibleForTesting
    internal fun isExecutionPaused(): Boolean = isExecutionPaused.value

    override fun setEnginePaused(paused: Boolean) {
        isPaused.update { paused }
        triggerProcessor.setPaused(paused)
    }

    override fun setExecutionPaused(paused: Boolean) {
        isExecutionPaused.update { paused }
    }

    override fun start() {
        restoreState.value = ScheduleRestoreState.IN_PROGRESS

        scope.launch {
            automationStoreMigrator.migrateData()
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

            launch {
                combine(isPaused, isExecutionPaused) { enginePaused, executionPaused ->
                    enginePaused || executionPaused
                }.distinctUntilChanged().collect { paused ->
                    if (isActive && !paused) {
                        scheduleConditionsChangedNotifier.notifyChanged()
                    }
                }
            }

            launch {
                pendingExecution.collect {
                    UALog.d { "Processing pending execution queue update ${it.map { it.scheduleId }}" }
                    processNextPendingExecution()
                }
            }
        }
    }

    fun stop() {
        restoreState.value = ScheduleRestoreState.IDLE
        supervisorJob.cancelChildren()
    }

    private suspend fun cancelPreprocessDelayJobs(): Unit = withContext(dispatcher) {
        preprocessingDelayJobs.removeAll {
            it.cancel()
            true
        }
    }

    private suspend fun preprocessDelay(data: AutomationScheduleData): Boolean = withContext(dispatcher) {
        val delay = data.schedule.delay ?: return@withContext true
        val scheduleId = data.schedule.identifier
        val triggerDate = data.triggerInfo?.date ?: data.scheduleStateChangeDate

        val job = async {
            UALog.v {"Preprocessing delay $scheduleId" }
            delayProcessor.preprocess(delay, triggerDate)
            UALog.v {"Finished preprocessing delay $scheduleId" }
        }

        preprocessingDelayJobs.add(job)
        job.join()
        preprocessingDelayJobs.remove(job)
        return@withContext job.isCompleted
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

        cancelPreprocessDelayJobs()
    }

    override suspend fun upsertSchedules(schedules: List<AutomationSchedule>) = withContext(dispatcher) {
        waitForScheduleRestore()

        val idToSchedule = schedules.associateBy { it.identifier }
        val idToScheduleKeys = idToSchedule.keys

        UALog.d { "Updating schedules $idToScheduleKeys" }

        val updatedSchedules = store.upsertSchedules(idToSchedule.keys.toList()) { identifier, data ->
            val schedule = requireNotNull(idToSchedule[identifier])
            val stored = schedule.updateOrCreate(data, clock.currentTimeMillis())
            stored.updateState(clock.currentTimeMillis())
        }

        triggerProcessor.updateSchedules(updatedSchedules)
        cancelPreprocessDelayJobs()
    }

    override suspend fun cancelSchedules(identifiers: List<String>) = withContext(dispatcher) {
        waitForScheduleRestore()

        UALog.d { "Cancelling schedules ${identifiers})" }

        store.deleteSchedules(identifiers)
        triggerProcessor.cancel(identifiers)
        cancelPreprocessDelayJobs()
    }

    override suspend fun cancelSchedules(group: String) = withContext(dispatcher) {
        waitForScheduleRestore()

        UALog.d { "Cancelling schedules with group ${group})" }

        store.deleteSchedules(group)
        triggerProcessor.cancel(group)
        cancelPreprocessDelayJobs()
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
        cancelPreprocessDelayJobs()
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

    private suspend fun processTriggerResult(result: TriggerResult) {
        val date = clock.currentTimeMillis()

        try {
            when(result.triggerExecutionType) {
                TriggerExecutionType.DELAY_CANCELLATION -> {
                    val data = updateState(result.scheduleId) { it.executionCancelled(date) }
                    data?.let { preparer.cancelled(it.schedule) }
                }
                TriggerExecutionType.EXECUTION -> {
                    updateState(result.scheduleId) { it.triggered(result.triggerInfo, date)}
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
                    handleInterval((updated.schedule.interval?.toLong() ?: 0L).seconds, data.schedule.identifier)
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
                val interval = (data.schedule.interval?.toLong() ?: 0).seconds
                val remaining = interval - (clock.currentTimeMillis() - data.scheduleStateChangeDate).milliseconds
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

    private suspend fun startTaskToProcessTriggeredSchedule(scheduleId: String) {
        scope.launch {
            UALog.v { "Processing triggered schedule $scheduleId" }
            processTriggeredSchedule(scheduleId)
        }
        // Give the task above a chance to run
        yield()
    }

    private suspend fun processTriggeredSchedule(scheduleId: String) {
        // Check if we are paused
        if (isExecutionPaused.value) {
            // Wait for it to be resumed
            isExecutionPaused.first { !it }
        }

        val data = store.getSchedule(scheduleId)
        if (data == null) {
            UALog.v { "Aborting processing schedule $scheduleId, no longer in database." }
            return
        }

        if (!data.isInState(listOf(AutomationScheduleState.TRIGGERED))) {
            UALog.v { "Aborting processing schedule $data, no longer triggered." }
            return
        }

        if (!preprocessDelay(data)) {
            UALog.v {"Preprocessing delay interrupted $data, retrying" }
            processTriggeredSchedule(scheduleId)
        }

        if (store.getSchedule(scheduleId) != data) {
            UALog.v {"Trigger data has changed since preprocessing, retrying $scheduleId" }
            processTriggeredSchedule(scheduleId)
            return
        }

        if (!data.isActive(clock.currentTimeMillis())) {
            UALog.v { "Aborting processing schedule $data, no longer active." }
            preparer.cancelled(data.schedule)
            return
        }

        prepareSchedule(data)?.let { processPrepared(it) }
    }

    private suspend fun processPrepared(preparedData: PreparedData) {
        waitForConditions(preparedData)

        if (!checkStillValid(preparedData)) {
            val updated = updateState(preparedData.scheduleId) {
                it.executionInvalidated(clock.currentTimeMillis())
            }

            if (updated?.scheduleState == AutomationScheduleState.TRIGGERED) {
                startTaskToProcessTriggeredSchedule(preparedData.scheduleId)
            } else {
                preparer.cancelled(preparedData.schedule.schedule)
            }

            return
        }

        pendingExecution.update { value ->
            value.toMutableSet().also { it.add(preparedData) }
        }
    }

    private suspend fun processNextPendingExecution() {
        val next = pendingExecution.value
            .minByOrNull { it.priority } ?: return

        UALog.d { "Processing next pending schedule for execution: ${next.schedule}" }

        pendingExecution.update { value ->
            value.toMutableSet().also { it.remove(next) }
        }

        val jobRan = MutableStateFlow(false)

        scope.launch {
            withContext(Dispatchers.Main) {
                jobRan.update { true }
                val isReady = checkStillValid(next) &&
                        delayProcessor.areConditionsMet(next.schedule.schedule.delay)

                if (!isReady) {
                    UALog.v { "Schedule no loner ready for execution ${next.schedule}" }
                    processPrepared(next)
                } else {
                    UALog.v { "Attempting to execute ${next.schedule}" }

                    val handled = attemptExecute(next.schedule, next.preparedSchedule)
                    UALog.v { "Execution attempt finished ${next.schedule}, success: $handled" }
                    if (!handled) {
                        pendingExecution.update { value ->
                            value.toMutableSet().also { it.add(next) }
                        }
                    }
                }
            }
        }

        jobRan.first { it }
    }

    private suspend fun checkStillValid(prepared: PreparedData): Boolean {
        // Make sure we are still up to date. Data might change due to a change
        // in the data, schedule was cancelled, or if a delay cancellation trigger
        // was fired.
        val fromStore = store.getSchedule(prepared.scheduleId)
        if (fromStore == null ||
            fromStore.scheduleState != AutomationScheduleState.PREPARED ||
            fromStore.schedule != prepared.schedule.schedule) {
            UALog.v { "Prepared schedule no longer up to date, no longer valid ${prepared.schedule}" }
            return false
        }

        if (!prepared.schedule.isActive(clock.currentTimeMillis())) {
            UALog.v { "Prepared schedule no longer active, no longer valid ${prepared.schedule}" }
            return false
        }

        if (!executor.isValid(prepared.schedule.schedule)) {
            UALog.v { "Prepared schedule no longer valid ${prepared.schedule}" }
            return false
        }

        return true
    }

    private suspend fun waitForConditions(preparedData: PreparedData) {
        val triggerDate = preparedData.schedule.triggerInfo?.date ?: preparedData.schedule.scheduleStateChangeDate
        // Wait for conditions
        UALog.v { "Waiting for delay conditions $preparedData" }

        delayProcessor.process(
            delay = preparedData.schedule.schedule.delay,
            triggerDate = triggerDate
        )

        UALog.v { "Delay conditions met $preparedData" }
    }

    private suspend fun prepareSchedule(data: AutomationScheduleData): PreparedData? {
        UALog.v { "Preparing schedule $data" }

        val result = preparer.prepare(data.schedule, data.triggerInfo?.context, data.triggerSessionId)
        UALog.v { "Preparing schedule $data result: $result" }

        val updated = updateState(data.schedule.identifier) {
            if (!it.isInState(listOf(AutomationScheduleState.TRIGGERED))) {
                UALog.v { "Schedule $data no longer triggered" }
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
                SchedulePrepareResult.Invalidate -> {
                    scope.launch {
                        startTaskToProcessTriggeredSchedule(data.schedule.identifier)
                    }
                    it
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
                PreparedData(updated, result.schedule)
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

    @MainThread
    private suspend fun attemptExecute(
        data: AutomationScheduleData,
        preparedSchedule: PreparedSchedule
    ) : Boolean {

        val scheduleID = data.schedule.identifier
        when (checkReady(data, preparedSchedule)) {
            ScheduleReadyResult.READY -> {}
            ScheduleReadyResult.INVALIDATE -> {
                val updated =
                    updateState(scheduleID) { it.executionInvalidated(clock.currentTimeMillis()) }
                if (updated?.scheduleState == AutomationScheduleState.TRIGGERED) {
                    startTaskToProcessTriggeredSchedule(scheduleID)
                } else {
                    preparer.cancelled(data.schedule)
                }
                return true
            }

            ScheduleReadyResult.NOT_READY -> {
                this.scheduleConditionsChangedNotifier.wait()
                return false
            }

            ScheduleReadyResult.SKIP -> {
                updateState(scheduleID) { it.executionSkipped(clock.currentTimeMillis()) }
                preparer.cancelled(data.schedule)
                return true
            }
        }

        UALog.v { "Executing schedule ${preparedSchedule.info.scheduleId}" }

        val updateStateJob = scope.launch {
            updateState(preparedSchedule.info.scheduleId) { it.executing(clock.currentTimeMillis()) }
        }

        val result = executor.execute(preparedSchedule)

        updateStateJob.join()

        UALog.v { "Executing result ${preparedSchedule.info.scheduleId} $result" }

        when (result) {
            ScheduleExecuteResult.CANCEL -> {
                store.deleteSchedules(listOf(scheduleID))
                triggerProcessor.cancel(listOf(scheduleID))
                return true
            }

            ScheduleExecuteResult.FINISHED -> {
                val update =
                    updateState(scheduleID) { it.finishedExecuting(clock.currentTimeMillis()) }
                if (update?.scheduleState == AutomationScheduleState.PAUSED) {
                    val interval = update.schedule.interval?.toLong() ?: 0L
                    handleInterval(interval.seconds, scheduleID)
                }
                return true
            }

            ScheduleExecuteResult.RETRY -> return false
        }

    }


    private fun checkReady(data: AutomationScheduleData, preparedSchedule: PreparedSchedule): ScheduleReadyResult {
        UALog.v { "Checking if schedule is ready $data" }

        if (isExecutionPaused.value || isPaused.value) {
            UALog.v { "Executor paused, not ready $data" }
            return ScheduleReadyResult.NOT_READY
        }

        if (!data.isActive(clock.currentTimeMillis())) {
            UALog.v { "Schedule no longer active, Invalidating $data" }
            return ScheduleReadyResult.INVALIDATE
        }

        val result = executor.isReady(preparedSchedule)
        if (result != ScheduleReadyResult.READY) {
            UALog.v { "Schedule not ready $data" }
        }

        return result
    }

    private fun handleInterval(interval: Duration, scheduleID: String) {
        UALog.v { "handleInterval(interval: $interval, scheduleID: $scheduleID)" }
        scope.launch {
            sleeper.sleep(interval)
            updateState(scheduleID) {
                it.idle(clock.currentTimeMillis())
            }
        }
    }

    private data class PreparedData(
        val schedule: AutomationScheduleData,
        val preparedSchedule: PreparedSchedule
    ) {
        val scheduleId: String = schedule.schedule.identifier
        val priority: Int = schedule.schedule.priority ?: 0
    }
}

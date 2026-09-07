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
import com.urbanairship.automation.limits.AutomationLedgerInterface
import com.urbanairship.automation.limits.LedgerExecutionResult
import com.urbanairship.automation.limits.LedgerGroupReservations
import com.urbanairship.automation.limits.LedgerLimitEvaluator
import com.urbanairship.automation.storage.AutomationStoreMigrator
import com.urbanairship.automation.updateOrCreate
import com.urbanairship.automation.utils.ScheduleConditionsChangedNotifier
import com.urbanairship.iam.InAppMessage
import com.urbanairship.util.Clock
import com.urbanairship.util.TaskSleeper
import com.urbanairship.util.minus
import kotlin.time.Duration
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

    /** Reconciles the ledger against the current schedules: retention + compaction. */
    suspend fun reconcileLedger()
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
    private val eventsHistory: EventsHistory,
    private val ledger: AutomationLedgerInterface,
    private val limitEvaluator: LedgerLimitEvaluator,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    private val sleeper: TaskSleeper = TaskSleeper.default,
    private val dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher(),
    /**
     * Serializes the executions that pool a budget through a shared ledger
     * group, so a sibling cannot read the tally while one of them is still
     * displaying and has not recorded yet.
     */
    private val groupReservations: LedgerGroupReservations = LedgerGroupReservations(),
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
                    eventsHistory.add(it)
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

        val timestamp = clock.now()
        for (item in identifiers) {
            updateState(item) { data ->
                data.setSchedule(data.schedule.copyWith(endDate = timestamp))
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

        // The ledger read is a suspending DB call and cannot run inside the
        // store's update block, so resolve each schedule's over-limit state up
        // front and feed it in.
        val overLimitById = idToSchedule.mapValues { (_, schedule) -> isOverLimit(schedule) }

        val updatedSchedules = store.upsertSchedules(idToSchedule.keys.toList()) { identifier, data ->
            val schedule = requireNotNull(idToSchedule[identifier])
            val stored = schedule.updateOrCreate(data, clock.now())
            stored.updateState(clock.now(), overLimitById[identifier] ?: false)
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

    override suspend fun reconcileLedger(): Unit = withContext(dispatcher) {
        waitForScheduleRestore()

        UALog.d { "Reconciling ledger" }

        // The persisted schedules, not the filtered public list: a schedule
        // lingers in the store through its edit grace period after dropping out
        // of a listing, so its ledger IDs stay live here until then — giving its
        // events a tail rather than dropping them the moment the listing
        // changes.
        val schedules = store.getSchedules().map { it.schedule }

        ledger.reconcile(
            liveScheduleIds = schedules.map { it.identifier }.toSet(),
            liveSharedIds = schedules.mapNotNull { it.ledgerConfig?.sharedId }.toSet()
        )
    }

    override suspend fun getSchedules(): List<AutomationSchedule> = withContext(dispatcher) {
        return@withContext store
            .getSchedules()
            .filter { !it.shouldDelete(clock.now()) }
            .map { it.schedule }
    }

    override suspend fun getSchedule(identifier: String): AutomationSchedule? = withContext(dispatcher) {
        val result = store.getSchedule(identifier) ?: return@withContext null
        if (result.isExpired(clock.now())) {
            return@withContext null
        }

        return@withContext result.schedule
    }

    override suspend fun getSchedules(group: String): List<AutomationSchedule> = withContext(dispatcher) {
        val date = clock.now()

        return@withContext store
            .getSchedules(group)
            .filter { !it.isExpired(date) }
            .map { it.schedule }
            .toList()
    }

    /** Whether the schedule has reached its limit according to the ledger. */
    private suspend fun isOverLimit(schedule: AutomationSchedule): Boolean =
        limitEvaluator.isOverLimit(schedule)

    /**
     * Looks the schedule up by ID before evaluating its ledger limit. Returns
     * `false` when the schedule can't be loaded, erring toward continuing rather
     * than silently finishing.
     */
    private suspend fun isOverLimit(scheduleId: String): Boolean {
        val data = store.getSchedule(scheduleId) ?: return false
        return isOverLimit(data.schedule)
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
        val date = clock.now()

        try {
            when(result.triggerExecutionType) {
                TriggerExecutionType.DELAY_CANCELLATION -> {
                    val isOverLimit = isOverLimit(result.scheduleId)
                    val data = updateState(result.scheduleId) {
                        it.executionCancelled(date, isOverLimit)
                    }
                    data?.let { preparer.cancelled(it.schedule) }
                }
                TriggerExecutionType.EXECUTION -> {
                    // `triggered` is a no-op unless the schedule is idle, so capture
                    // whether this call is the one that moved it into TRIGGERED. A
                    // redundant trigger result leaves the state alone and records nothing.
                    var didTrigger = false
                    val isOverLimit = isOverLimit(result.scheduleId)
                    val updated = updateState(result.scheduleId) { data ->
                        val wasIdle = data.scheduleState == AutomationScheduleState.IDLE
                        data.triggered(result.triggerInfo, date, isOverLimit).also {
                            didTrigger = wasIdle &&
                                    it.scheduleState == AutomationScheduleState.TRIGGERED
                        }
                    }

                    if (didTrigger && updated != null) {
                        ledger.recordTriggered(
                            scheduleId = updated.schedule.identifier,
                            sharedId = updated.schedule.ledgerConfig?.sharedId,
                            triggerId = result.triggerInfo.triggerId
                        )
                    }
                    startTaskToProcessTriggeredSchedule(result.scheduleId)
                }
            }
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to process trigger result $result" }
        }
    }

    private suspend fun restoreSchedules() {
        val now = clock.now()

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
                val retry = behavior == InterruptedBehavior.RETRY

                if (!retry) {
                    // The schedule executed - it was displaying or running actions
                    // when the app went away - so it consumed budget. Record the
                    // outcome unless the executor already got to, then read the
                    // ledger with it counted.
                    ledger.recordExecutionIfNoneSince(
                        scheduleId = data.schedule.identifier,
                        sharedId = preparedInfo.ledgerSharedId,
                        triggerId = preparedInfo.triggerId,
                        result = LedgerExecutionResult.SUCCEEDED,
                        cancel = false,
                        since = data.scheduleStateChangeDate
                    )
                }

                val isOverLimit = isOverLimit(data.schedule)
                updated = updateState(data.schedule.identifier) {
                    it.executionInterrupted(now, retry = retry, isOverLimit = isOverLimit)
                }
                if (updated?.scheduleState == AutomationScheduleState.PAUSED) {
                    handleInterval(updated.schedule.interval ?: Duration.ZERO, data.schedule.identifier)
                }
            } else {
                val isOverLimit = isOverLimit(data.schedule)
                updated = updateState(data.schedule.identifier) {
                    it.prepareInterrupted(now, isOverLimit)
                }
            }

            if (updated?.scheduleState == AutomationScheduleState.TRIGGERED) {
                startTaskToProcessTriggeredSchedule(updated.schedule.identifier)
            }
        }

        // Restore Intervals
        schedules
            .filter { it.scheduleState == AutomationScheduleState.PAUSED }
            .forEach { data ->
                val interval = data.schedule.interval ?: Duration.ZERO
                val remaining = interval - (clock.now() - data.scheduleStateChangeDate)
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

        if (!data.isActive(clock.now())) {
            UALog.v { "Aborting processing schedule $data, no longer active." }
            preparer.cancelled(data.schedule)
            return
        }

        prepareSchedule(data)?.let { processPrepared(it) }
    }

    private suspend fun processPrepared(preparedData: PreparedData) {
        waitForConditions(preparedData)

        if (!checkStillValid(preparedData)) {
            val isOverLimit = isOverLimit(preparedData.schedule.schedule)
            val updated = updateState(preparedData.scheduleId) {
                it.executionInvalidated(clock.now(), isOverLimit)
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

        if (!prepared.schedule.isActive(clock.now())) {
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

        val result = preparer.prepare(
            data.schedule,
            data.triggerInfo?.context,
            data.triggerSessionId,
            data.triggerInfo?.triggerId
        )
        UALog.v { "Preparing schedule $data result: $result" }

        // Read the ledger after prepare so a penalizing audience miss, whose
        // `audience_miss` event is recorded during prepare, is already counted.
        // Only the transitions below consult it, so skip the read otherwise.
        val isOverLimit = when (result) {
            is SchedulePrepareResult.Prepared,
            SchedulePrepareResult.Penalize,
            SchedulePrepareResult.Skip -> isOverLimit(data.schedule)
            else -> false
        }

        val updated = updateState(data.schedule.identifier) {
            if (!it.isInState(listOf(AutomationScheduleState.TRIGGERED))) {
                UALog.v { "Schedule $data no longer triggered" }
                return@updateState it
            }

            return@updateState when(result) {
                is SchedulePrepareResult.Prepared -> {
                    it.prepared(result.schedule.info, clock.now(), isOverLimit)
                }
                SchedulePrepareResult.Penalize -> {
                    it.prepareCancelled(clock.now(), penalize = true, isOverLimit = isOverLimit)
                }
                SchedulePrepareResult.Skip -> {
                    it.prepareCancelled(clock.now(), penalize = false, isOverLimit = isOverLimit)
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
                // Make sure the transition actually applied. The schedule might have left
                // the TRIGGERED state while prepare was in flight (e.g., a delay
                // cancellation trigger fired), making `prepared` a no-op.
                if (updated.scheduleState == AutomationScheduleState.PREPARED &&
                    updated.preparedScheduleInfo == result.schedule.info) {
                    PreparedData(updated, result.schedule)
                } else {
                    preparer.cancelled(data.schedule)
                    null
                }
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
        val sharedId = data.schedule.ledgerConfig?.sharedId

        // Schedules pooling a budget through `ledger_config.shared_id` are
        // limited by a tally another schedule can add to, and the last check ran
        // at prepare time — before the delay conditions and the pending
        // execution queue, so arbitrarily long ago. Re-read before spending it.
        //
        // Ahead of `checkReady` because that charges the schedule's frequency
        // constraints, and an attempt this drops must not spend an occurrence.
        //
        // Only worth reading for a pooled schedule: with no shared ID the sole
        // writer of a counting event is this schedule's own execution, which
        // cannot have happened while it sat here prepared.
        //
        // A reserving schedule re-reads again after taking its group, so on an
        // uncontended group this looks like one read too many. It is not: this
        // one drops a spent schedule without first queueing behind a sibling's
        // whole display, and it is the only check a non-reserving pooled
        // schedule gets.
        if (sharedId != null && isOverLimit(data.schedule)) {
            UALog.v { "Ledger group spent since prepare, skipping $scheduleID" }
            skipOverLimit(data)
            return true
        }

        // A group only needs serializing while something in it is executing, so
        // a schedule that reserves nothing runs straight through.
        val reservedGroupId = sharedId?.takeIf { preparedSchedule.reservesLedgerGroup }
            ?: return settle(runAttempt(data, preparedSchedule), data)

        // Hold the group across the execution. The check above cannot see a
        // sibling that is mid-display, because its event is not written until
        // the display ends — the reservation covers exactly that window.
        // Suspending here holds nothing else up: the pending-execution drain
        // dispatches attempts without awaiting them, so only this group queues
        // behind the holder.
        val attempt = groupReservations.withGroup(reservedGroupId) {
            // Waking can be much later — a sibling's display may have run for
            // minutes — so nothing established before the wait still holds. The
            // previous holder has recorded by now, so the ledger can answer.
            if (isOverLimit(data.schedule)) {
                UALog.v { "Ledger group $reservedGroupId spent while waiting, skipping $scheduleID" }
                skipOverLimit(data)
                return@withGroup AttemptOutcome.Settled(handled = true)
            }

            // `checkReady` covers pause, expiry and display readiness, but not
            // whether the definition still exists — that is `checkStillValid`,
            // which the drain runs microseconds before dispatching. Waiting on
            // the group makes that gap unbounded, long enough for a remote-data
            // refresh to remove or replace the campaign.
            if (!checkStillValid(PreparedData(data, preparedSchedule))) {
                UALog.v { "No longer valid after waiting on group $reservedGroupId: $scheduleID" }
                return@withGroup AttemptOutcome.Unready(NotReadyVerdict.INVALIDATED)
            }

            runAttempt(data, preparedSchedule)
        }

        return settle(attempt, data)
    }

    /**
     * What an attempt left for its caller.
     *
     * Only an unready verdict escapes a ledger-group reservation: handling
     * [Unready.verdict] can wait on the conditions notifier, and every sibling
     * in the group would stall behind it. Everything else an attempt settles is
     * bounded, so it is done in place while the group is still held.
     */
    private sealed class AttemptOutcome {
        /** The attempt is finished with; [handled] is `attemptExecute`'s result. */
        data class Settled(val handled: Boolean) : AttemptOutcome()

        /** Nothing ran, and this verdict still needs applying. */
        data class Unready(val verdict: NotReadyVerdict) : AttemptOutcome()
    }

    /**
     * Why an attempt did not run.
     *
     * Kept separate from [ScheduleReadyResult] so READY cannot reach the
     * handler and force a dead branch, and so a schedule whose definition went
     * stale while it waited can say that rather than borrowing a readiness
     * verdict it never got.
     */
    private enum class NotReadyVerdict {
        /** The definition is gone or no longer current. */
        INVALIDATED,

        /** Conditions are not met; wait for them to change and retry. */
        WAIT_FOR_CONDITIONS,

        /** This attempt is spent without executing. */
        SKIP
    }

    /** The verdict this readiness result implies, or null when it is READY. */
    private fun ScheduleReadyResult.asNotReadyVerdict(): NotReadyVerdict? = when (this) {
        ScheduleReadyResult.READY -> null
        ScheduleReadyResult.INVALIDATE -> NotReadyVerdict.INVALIDATED
        ScheduleReadyResult.NOT_READY -> NotReadyVerdict.WAIT_FOR_CONDITIONS
        ScheduleReadyResult.SKIP -> NotReadyVerdict.SKIP
    }

    private suspend fun settle(outcome: AttemptOutcome, data: AutomationScheduleData): Boolean =
        when (outcome) {
            is AttemptOutcome.Settled -> outcome.handled
            is AttemptOutcome.Unready -> handleNotReady(outcome.verdict, data)
        }

    /** Finishes a schedule whose pooled budget is already spent. */
    private suspend fun skipOverLimit(data: AutomationScheduleData) {
        updateState(data.schedule.identifier) {
            it.executionSkipped(clock.now(), isOverLimit = true)
        }
        preparer.cancelled(data.schedule)
    }

    /**
     * Checks readiness and, when ready, executes and applies the outcome.
     *
     * Safe to run holding a ledger group: everything it settles is bounded. A
     * non-ready verdict is handed back rather than handled, since handling it
     * is not.
     */
    @MainThread
    private suspend fun runAttempt(
        data: AutomationScheduleData,
        preparedSchedule: PreparedSchedule
    ): AttemptOutcome {
        checkReady(data, preparedSchedule).asNotReadyVerdict()?.let {
            return AttemptOutcome.Unready(it)
        }

        val scheduleID = data.schedule.identifier

        UALog.v { "Executing schedule ${preparedSchedule.info.scheduleId}" }

        val updateStateJob = scope.launch {
            updateState(preparedSchedule.info.scheduleId) { it.executing(clock.now()) }
        }

        val result = executor.execute(preparedSchedule)

        updateStateJob.join()

        UALog.v { "Executing result ${preparedSchedule.info.scheduleId} $result" }

        return when (result) {
            ScheduleExecuteResult.CANCEL -> {
                store.deleteSchedules(listOf(scheduleID))
                triggerProcessor.cancel(listOf(scheduleID))
                AttemptOutcome.Settled(handled = true)
            }

            ScheduleExecuteResult.FINISHED -> {
                // The execution ledger event is recorded during `execute`, so the
                // read here counts it when deciding whether the limit is hit.
                val isOverLimit = isOverLimit(data.schedule)
                val update =
                    updateState(scheduleID) { it.finishedExecuting(clock.now(), isOverLimit) }
                if (update?.scheduleState == AutomationScheduleState.PAUSED) {
                    handleInterval(update.schedule.interval ?: Duration.ZERO, scheduleID)
                }
                AttemptOutcome.Settled(handled = true)
            }

            ScheduleExecuteResult.RETRY -> AttemptOutcome.Settled(handled = false)
        }
    }

    /** Applies the verdict of an attempt that did not run. */
    private suspend fun handleNotReady(
        verdict: NotReadyVerdict,
        data: AutomationScheduleData
    ): Boolean {
        val scheduleID = data.schedule.identifier

        return when (verdict) {
            NotReadyVerdict.INVALIDATED -> {
                val isOverLimit = isOverLimit(data.schedule)
                val updated =
                    updateState(scheduleID) { it.executionInvalidated(clock.now(), isOverLimit) }
                if (updated?.scheduleState == AutomationScheduleState.TRIGGERED) {
                    startTaskToProcessTriggeredSchedule(scheduleID)
                } else {
                    preparer.cancelled(data.schedule)
                }
                true
            }

            NotReadyVerdict.WAIT_FOR_CONDITIONS -> {
                this.scheduleConditionsChangedNotifier.wait()
                false
            }

            NotReadyVerdict.SKIP -> {
                val isOverLimit = isOverLimit(data.schedule)
                updateState(scheduleID) { it.executionSkipped(clock.now(), isOverLimit) }
                preparer.cancelled(data.schedule)
                true
            }
        }
    }


    private fun checkReady(data: AutomationScheduleData, preparedSchedule: PreparedSchedule): ScheduleReadyResult {
        UALog.v { "Checking if schedule is ready $data" }

        if (isExecutionPaused.value || isPaused.value) {
            UALog.v { "Executor paused, not ready $data" }
            return ScheduleReadyResult.NOT_READY
        }

        if (!data.isActive(clock.now())) {
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
                it.idle(clock.now())
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

/**
 * Whether an execution of this schedule should hold its ledger group for the
 * duration.
 *
 * Embedded messages are excluded: they are placed into a host view instead of
 * taking over the screen, so several are live at once by design and one can
 * stay live indefinitely. Holding the group across one would stall its siblings
 * for as long as the host view shows it.
 */
private val PreparedSchedule.reservesLedgerGroup: Boolean
    get() = when (val data = data) {
        is PreparedScheduleData.Action -> true
        is PreparedScheduleData.InAppMessage -> !data.inAppMessage.isEmbedded()
    }

/** The message a prepared in-app schedule will display. */
private val PreparedScheduleData.InAppMessage.inAppMessage: InAppMessage
    get() = message.message

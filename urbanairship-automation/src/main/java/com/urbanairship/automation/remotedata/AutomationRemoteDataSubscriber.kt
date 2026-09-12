/* Copyright Airship and Contributors */

package com.urbanairship.automation.remotedata

import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import com.urbanairship.Airship
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.InAppAutomationRemoteDataStatus
import com.urbanairship.automation.engine.AutomationEngineInterface
import com.urbanairship.automation.isNewSchedule
import com.urbanairship.automation.limits.FrequencyConstraint
import com.urbanairship.automation.limits.FrequencyLimitManager
import com.urbanairship.remotedata.RemoteDataSource
import java.time.Instant
import kotlin.collections.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal class AutomationRemoteDataSubscriber (
    private val sourceInfoStore: AutomationSourceInfoStore,
    private val remoteDataAccess: AutomationRemoteDataAccessInterface,
    private val engine: AutomationEngineInterface,
    private val frequencyLimitManager: FrequencyLimitManager,
    private val airshipSDKVersion: String = Airship.version,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.IO
)  {

    private val scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob())
    private val subscriptionState = MutableStateFlow(false)

    init {
        scope.launch {
            var subscription: Job? = null
            subscriptionState.collect {
                if (it) {
                    subscription = scope.launch {
                        remoteDataAccess.updatesFlow.collect { payloads ->
                            UALog.v {
                                val sourceInfo = payloads.payload.map {  payload ->
                                    "${payload.value.remoteDataInfo?.source}: ${payload.value.remoteDataInfo?.lastModified}"
                                }

                                "Received automation payloads: $sourceInfo"
                            }

                            if (!processConstraints(payloads)) {
                                UALog.w { "Failed to process constraints, skipping update." }
                                return@collect
                            }

                            processAutomations(payloads)
                            UALog.v { "Subscriber finished update" }
                        }
                    }
                } else {
                    subscription?.cancel()
                }
            }
        }
    }

    fun subscribe() {
        subscriptionState.compareAndSet(expect = false, update = true)
    }

    fun unsubscribe() {
        subscriptionState.compareAndSet(expect = true, update = false)
    }

    val status: InAppAutomationRemoteDataStatus
        get() = remoteDataAccess.status

    val statusUpdates: Flow<InAppAutomationRemoteDataStatus>
        get() = remoteDataAccess.statusUpdates

    private suspend fun processAutomations(data: InAppRemoteData) {
        UALog.v { "Processing automations" }

        val currentSchedules = engine.getSchedules()

        RemoteDataSource.entries.forEach { source ->
            val schedules = currentSchedules.filter { remoteDataAccess.sourceFor(it) == source }
            syncAutomations(data.payload[source], source, schedules)
        }

        // Now that the schedules missing from the listing have been stopped,
        // clean up the ledger against what remains. Cleanup must never break
        // syncing, so failures are logged and swallowed - except cancellation,
        // which has to keep propagating out of the collector.
        try {
            engine.reconcileLedger()
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to reconcile ledger" }
        }
    }

    private suspend fun processConstraints(data: InAppRemoteData): Boolean {
        UALog.v { "Processing constraints" }

        val constraints = data.payload.values
            .mapNotNull { it.data.constraints }
            .fold(emptyList<FrequencyConstraint>()) { acc, next -> acc + next}

        val result = frequencyLimitManager.setConstraints(constraints)
        if (result.isFailure) {
            UALog.w { "Failed to process constraints ${result.exceptionOrNull()}" }
        }
        return result.isSuccess
    }

    private suspend fun syncAutomations(
        payload: InAppRemoteData.Payload?,
        source: RemoteDataSource,
        current: List<AutomationSchedule>
    ) {

        val currentScheduleIDs = current.map { it.identifier }.toSet()

        if (payload == null) {
            if (currentScheduleIDs.isNotEmpty()) {
                engine.stopSchedules(currentScheduleIDs.toList())
            }
            return
        }

        val contactID = payload.remoteDataInfo?.contactId
        val lastSourceInfo = sourceInfoStore.getSourceInfo(source, contactID)

        val failureResolution = resolveFailedSchedules(
            lastSourceInfo = lastSourceInfo,
            currentFailures = payload.data.failedSchedules
        )

        val currentSourceInfo = AutomationSourceInfo(
            remoteDataInfo = payload.remoteDataInfo,
            payloadTimestamp = payload.timestamp,
            airshipSDKVersion = airshipSDKVersion,
            failedSchedules = failureResolution.tracked.ifEmpty { null }
        )

        if (currentSourceInfo == lastSourceInfo) {
            UALog.v { "Up to date, skipping for source $source" }
            return
        }

        val identifiers = payload.data.schedules.map { it.identifier }.toSet()
        val toStop = current
            .filter { !identifiers.contains(it.identifier) }
            .map { it.identifier }

        if (toStop.isNotEmpty()) {
            engine.stopSchedules(toStop)
        }

        val toUpsert = payload.data.schedules.filter { schedule ->
            // If we have an ID for this schedule then its either unchanged or updated
            if (currentScheduleIDs.contains(schedule.identifier)) {
                return@filter true
            }

            // A schedule we previously failed to parse now parses. It was never applied, so the
            // timestamp check below would wrongly treat it as already handled.
            if (failureResolution.recovered.contains(schedule.identifier)) {
                return@filter true
            }

            // Otherwise check to see if we consider this a new schedule based on timestamp
            // and SDK version
            schedule.isNewSchedule(
                sinceDate = lastSourceInfo?.payloadTimestamp ?: Instant.EPOCH,
                lastSDKVersion = lastSourceInfo?.airshipSDKVersion
            )
        }

        if (toUpsert.isNotEmpty()) {
            engine.upsertSchedules(toUpsert)
        }

        sourceInfoStore.setSourceInfo(currentSourceInfo, source, contactID)
    }

    /**
     * Diffs the failures recorded on the last sync against the current ones.
     *
     * [FailureResolution.tracked] carries still-failing records forward with their original
     * created date and min SDK version, so a retry is evaluated against the payload that first
     * dropped them rather than the latest one.
     *
     * @param lastSourceInfo The checkpoint written by the previous sync for this source, or null
     * if we have never synced it. Supplies both the previously tracked failures and the timestamp
     * and SDK version a new failure is judged against.
     * @param currentFailures The schedules that failed to parse in the payload being processed.
     * @return The records to persist on the new checkpoint, and the ids that stopped failing and
     * therefore need to bypass the timestamp check when they are upserted.
     */
    private fun resolveFailedSchedules(
        lastSourceInfo: AutomationSourceInfo?,
        currentFailures: List<FailedScheduleRecord>
    ): FailureResolution {
        val previouslyFailed = lastSourceInfo?.failedSchedules ?: emptyList()
        val currentlyFailedIDs = currentFailures.map { it.identifier }.toSet()

        val recovered = previouslyFailed
            .map { it.identifier }
            .filter { !currentlyFailedIDs.contains(it) }
            .toSet()

        val stillFailing = previouslyFailed.filter { currentlyFailedIDs.contains(it.identifier) }
        val stillFailingIDs = stillFailing.map { it.identifier }.toSet()

        val newlyFailed = currentFailures
            .filter { !stillFailingIDs.contains(it.identifier) }
            .filter {
                isNewSchedule(
                    created = it.createdDate.toULong(),
                    minSDKVersion = it.minSDKVersion,
                    sinceDate = (lastSourceInfo?.payloadTimestamp ?: Instant.EPOCH).toEpochMilli(),
                    lastSDKVersion = lastSourceInfo?.airshipSDKVersion
                )
            }

        // Sorted so payload ordering can't produce a spurious source info change.
        return FailureResolution(
            tracked = (stillFailing + newlyFailed).sortedBy { it.identifier },
            recovered = recovered
        )
    }

    private data class FailureResolution(
        val tracked: List<FailedScheduleRecord>,
        val recovered: Set<String>
    )
}

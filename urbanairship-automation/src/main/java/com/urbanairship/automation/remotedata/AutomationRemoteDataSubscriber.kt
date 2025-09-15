/* Copyright Airship and Contributors */

package com.urbanairship.automation.remotedata

import com.urbanairship.AirshipDispatchers
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.InAppAutomationRemoteDataStatus
import com.urbanairship.automation.engine.AutomationEngineInterface
import com.urbanairship.automation.isNewSchedule
import com.urbanairship.automation.limits.FrequencyConstraint
import com.urbanairship.automation.limits.FrequencyLimitManager
import com.urbanairship.remotedata.RemoteDataSource
import kotlin.collections.map
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal class AutomationRemoteDataSubscriber (
    dataStore: PreferenceDataStore,
    private val remoteDataAccess: AutomationRemoteDataAccessInterface,
    private val engine: AutomationEngineInterface,
    private val frequencyLimitManager: FrequencyLimitManager,
    private val airshipSDKVersion: String = UAirship.getVersion(),
    dispatcher: CoroutineDispatcher = AirshipDispatchers.IO
)  {

    private val scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob())
    private val sourceInfoStore = AutomationSourceInfoStore(dataStore)
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

        val currentScheduleIDs = current.map { it.identifier }

        if (payload == null) {
            if (currentScheduleIDs.isNotEmpty()) {
                engine.stopSchedules(currentScheduleIDs)
            }
            return
        }

        val contactID = payload.remoteDataInfo?.contactId
        val lastSourceInfo = sourceInfoStore.getSourceInfo(source, contactID)

        val currentSourceInfo = AutomationSourceInfo(
            remoteDataInfo = payload.remoteDataInfo,
            payloadTimestamp = payload.timestamp,
            airshipSDKVersion = airshipSDKVersion
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

            // Otherwise check to see if we consider this a new schedule based on timestamp
            // and SDK version
            schedule.isNewSchedule(
                sinceDate = lastSourceInfo?.payloadTimestamp ?: 0L,
                lastSDKVersion = lastSourceInfo?.airshipSDKVersion
            )
        }

        if (toUpsert.isNotEmpty()) {
            engine.upsertSchedules(toUpsert)
        }

        sourceInfoStore.setSourceInfo(currentSourceInfo, source, contactID)
    }
}

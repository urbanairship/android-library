package com.urbanairship.automation.rewrite.remotedata

import androidx.annotation.RestrictTo
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UAirship
import com.urbanairship.automation.rewrite.AutomationEngineInterface
import com.urbanairship.automation.rewrite.AutomationSchedule
import com.urbanairship.automation.rewrite.isNewSchedule
import com.urbanairship.automation.rewrite.limits.FrequencyConstraint
import com.urbanairship.automation.rewrite.limits.FrequencyLimitManagerInterface
import com.urbanairship.remotedata.RemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AutomationRemoteDataSubscriberInterface {
    public fun subscribe()
    public fun unsubscribe()
}

internal class AutomationRemoteDataSubscriber (
    dataStore: PreferenceDataStore,
    private val remoteDataAccess: AutomationRemoteDataAccessInterface,
    private val engine: AutomationEngineInterface,
    private val frequencyLimitManager: FrequencyLimitManagerInterface,
    private val airshipSDKVersion: String = UAirship.getVersion(),
    private val scope: CoroutineScope = CoroutineScope(AirshipDispatchers.IO + SupervisorJob())
) : AutomationRemoteDataSubscriberInterface {

    private val sourceInfoStore = AutomationSourceInfoStore(dataStore)
    private var processJob: Job? = null

    override fun subscribe() {
        if (synchronized(this) { processJob != null }) { return }

        processJob = scope.launch {
            remoteDataAccess.updatesFlow.collect { payloads ->
                yield()
                processConstraints(payloads)
                processAutomations(payloads)
            }
        }
    }

    override fun unsubscribe() {
        synchronized(this) {
            processJob?.cancel()
            processJob = null
        }
    }

    private suspend fun processAutomations(data: InAppRemoteData) {
        val currentSchedules = engine.getSchedules()

        RemoteDataSource.entries.forEach { source ->
            val schedules = currentSchedules.filter { remoteDataAccess.sourceFor(it) == source }
            syncAutomations(data.payload[source], source, schedules)
        }
    }

    private suspend fun processConstraints(data: InAppRemoteData) {
        val constraints = data.payload.values
            .mapNotNull { it.data.constraints }
            .fold(emptyList<FrequencyConstraint>()) { acc, next -> acc + next}

        frequencyLimitManager.setConstraints(constraints)
    }

    private suspend fun syncAutomations(
        payload: InAppRemoteData.Payload?,
        source: RemoteDataSource,
        current: List<AutomationSchedule>) {

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

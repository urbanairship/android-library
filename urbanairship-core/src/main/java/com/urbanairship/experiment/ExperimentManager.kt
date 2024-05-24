/* Copyright Airship and Contributors */

package com.urbanairship.experiment

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipComponentGroups
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UALog
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.remotedata.RemoteData
import com.urbanairship.util.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Airship Experiment Manager.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@OpenForTesting
public class ExperimentManager internal constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    private val remoteData: RemoteData,
    private val clock: Clock = Clock.DEFAULT_CLOCK
) : AirshipComponent(context, dataStore) {

    private val scope = CoroutineScope(AirshipDispatchers.IO + SupervisorJob())

    public companion object {
        internal const val PAYLOAD_TYPE = "experiments"
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun getComponentGroup(): Int = AirshipComponentGroups.EXPERIMENT

    /**
     * Returns an optional Experiment with the given [id].
     *
     * @param messageInfo The message info.
     * @param id The ID of the Experiment.
     */
    internal suspend fun getExperimentWithId(messageInfo: MessageInfo, id: String): Experiment? {
        return getActiveExperiments(messageInfo)
            .find { it.id == id }
    }

    /**
     * Checks if the channel and/or contact is part of a global holdout or not.
     * @hide
     *
     * @param messageInfo The message info.
     * @param deviceInfoProvider The device info provider.
     * @return The experiments result. If no experiment matches, null is returned.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public suspend fun evaluateExperiments(messageInfo: MessageInfo, deviceInfoProvider: DeviceInfoProvider): Result<ExperimentResult?> {
        val activeExperiments = getActiveExperiments(messageInfo)
        if (activeExperiments.isEmpty()) {
            return Result.success(null)
        }

        val channelId = deviceInfoProvider.getChannelId()
        val contactId = deviceInfoProvider.getStableContactInfo().contactId

        val allExperimentsMetadata: MutableList<JsonMap> = mutableListOf()
        var matchedExperiment: Experiment? = null

        for (experiment in activeExperiments) {
            val isMatching = getResolutionFunction(experiment)
                .invoke(experiment, deviceInfoProvider)

            allExperimentsMetadata.add(experiment.reportingMetadata)

            if (isMatching) {
                matchedExperiment = experiment
                break
            }
        }

        return Result.success(
            ExperimentResult(
                channelId = channelId,
                contactId = contactId,
                matchedExperimentId = matchedExperiment?.id,
                isMatching = (matchedExperiment != null),
                allEvaluatedExperimentsMetadata = allExperimentsMetadata
            )
        )
    }

    private fun getResolutionFunction(experiment: Experiment): ResolutionFunction {
        return when (experiment.resolutionType) {
            ResolutionType.STATIC -> this::resolveStatic
            ResolutionType.DEFERRED -> this::resolveDeferred
        }
    }

    private suspend fun resolveDeferred(
        experiment: Experiment,
        infoProvider: DeviceInfoProvider,
    ): Boolean {
        return false
    }

    private suspend fun resolveStatic(
        experiment: Experiment,
        infoProvider: DeviceInfoProvider
    ): Boolean {
        return experiment.audience.evaluate(experiment.created, infoProvider)
    }

    private suspend fun getActiveExperiments(messageInfo: MessageInfo): List<Experiment> {
        return try {
            remoteData.payloads(PAYLOAD_TYPE)
                .mapNotNull {
                    it.data.opt(PAYLOAD_TYPE).list?.list
                }
                .flatten()
                .map { it.optMap() }
                .mapNotNull(Experiment::fromJson)
                .filter { it.isActive(clock.currentTimeMillis()) }
                .filter { experiment ->
                    !(experiment.exclusions.any { it.evaluate(messageInfo) })
                }
        } catch (ex: JsonException) {
            UALog.e(ex) { "Failed to parse experiments from remoteData payload" }
            emptyList()
        }
    }
}

private typealias ResolutionFunction = suspend (Experiment, DeviceInfoProvider) -> Boolean

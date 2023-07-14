/* Copyright Airship and Contributors */

package com.urbanairship.experiment

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipComponentGroups
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PendingResult
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UALog
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.remotedata.RemoteData
import com.urbanairship.util.Clock
import kotlin.jvm.Throws
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Airship Experiment Manager.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@OpenForTesting
public class ExperimentManager internal constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    private val remoteData: RemoteData,
    private val infoProvider: DeviceInfoProvider,
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
     * @param id The ID of the Experiment.
     */
    internal suspend fun getExperimentWithId(messageInfo: MessageInfo, id: String): Experiment? {
        return getActiveExperiments(messageInfo)
            .find { it.id == id }
    }

    /**
     * Checks if the channel and/or contact is part of a global holdout or not.
     * @param contactId The contact ID. If not provided, the stable contact ID will be used.
     * @return The experiments result. If no experiment matches, null is returned.
     */
    /** @hide */
    @Throws(NullPointerException::class)
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public suspend fun evaluateExperiments(messageInfo: MessageInfo, contactId: String? = null): ExperimentResult? {

        val activeExperiments = getActiveExperiments(messageInfo)
        if (activeExperiments.isEmpty()) {
            return null
        }

        val channelId = infoProvider.channelId
            ?: throw NullPointerException("Channel ID missing, unable to evaluate hold out groups.")

        val evaluationContactId = contactId ?: infoProvider.getStableContactId()

        val allExperimentsMetadata: MutableList<JsonMap> = mutableListOf()
        var matchedExperiment: Experiment? = null

        for (experiment in activeExperiments) {
            val isMatching = getResolutionFunction(experiment)
                .invoke(experiment, infoProvider, evaluationContactId)

            allExperimentsMetadata.add(experiment.reportingMetadata)

            if (isMatching) {
                matchedExperiment = experiment
                break
            }
        }

        return ExperimentResult(
            channelId = channelId,
            contactId = evaluationContactId,
            matchedExperimentId = matchedExperiment?.id,
            isMatching = (matchedExperiment != null),
            allEvaluatedExperimentsMetadata = allExperimentsMetadata)
    }

    /**
     * Checks if the channel and/or contact is part of a global holdout or not.
     * @param contactId The contact ID. If not provided, the stable contact ID will be used.
     * @return The pending experiment result. If no experiment matches, null result is returned.
     */
    public fun evaluateGlobalHoldoutsPendingResult(messageInfo: MessageInfo, contactId: String? = null): PendingResult<ExperimentResult?> {
        val result = PendingResult<ExperimentResult?>()
        scope.launch {
            result.result = evaluateExperiments(messageInfo, contactId)
        }
        return result
    }

    private fun getResolutionFunction(experiment: Experiment): ResolutionFunction {
        return when (experiment.resolutionType) {
            ResolutionType.STATIC -> this::resolveStatic
        }
    }

    private suspend fun resolveStatic(
        experiment: Experiment,
        infoProvider: DeviceInfoProvider,
        contactId: String
    ): Boolean {
        return experiment.audience.evaluate(context, experiment.created, infoProvider, contactId)
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

private typealias ResolutionFunction = suspend (Experiment, DeviceInfoProvider, String) -> Boolean

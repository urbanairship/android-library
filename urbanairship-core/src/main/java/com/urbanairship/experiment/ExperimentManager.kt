/* Copyright Airship and Contributors */

package com.urbanairship.experiment

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipComponentGroups
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.remotedata.RemoteData
import com.urbanairship.util.FarmHashFingerprint64

/**
 * Airship Experiment Manager.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ExperimentManager internal constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    private val remoteData: RemoteData,
    private val channelIdFetcher: () -> String?,
    private val stableContactIdFetcher: suspend () -> String
) : AirshipComponent(context, dataStore) {

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
    internal suspend fun getExperimentWithId(id: String): Experiment? {
        return getExperiments()
            .find { it.id == id }
    }

    /**
     * Checks if the channel and/or contact is part of a global holdout or not.
     * @param contactId The contact ID. If not provided, the stable contact ID will be used.
     * @return The experiments result. If no experiment matches, null is returned.
     */
    internal suspend fun evaluateGlobalHoldouts(messageInfo: MessageInfo, contactId: String? = null): ExperimentResult? {
        val channelId = channelIdFetcher() ?: return null
        val evaluationContactId = contactId ?: stableContactIdFetcher()

        val properties = mapOf(
            HashIdentifiers.CONTACT.jsonValue to evaluationContactId,
            HashIdentifiers.CHANNEL.jsonValue to channelId
        )

        var result: ExperimentResult? = null

        for (experiment in getExperiments()) {
            val isResolved = getResolutionFunction(experiment)
                .invoke(experiment, messageInfo, properties)

            if (isResolved) {
                result = ExperimentResult(
                    channelId = channelId,
                    contactId = evaluationContactId,
                    experimentId = experiment.id,
                    reportingMetadata = experiment.reportingMetadata
                )
                break
            }
        }

        return result
    }

    private fun getResolutionFunction(experiment: Experiment): ResolutionFunction {
        return when (experiment.resolutionType) {
            ResolutionType.STATIC -> this::resolveStatic
        }
    }

    private fun resolveStatic(
        experiment: Experiment,
        messageInfo: MessageInfo,
        properties: Map<String, String?>
    ): Boolean {
        if (experiment.exclusions.any { it.evaluate(messageInfo) }) {
            return false
        }

        return experiment.audienceSelector.isMatching(properties)
    }

    private suspend fun getExperiments(): List<Experiment> {
        try {
            return remoteData.payloads(PAYLOAD_TYPE)
                .mapNotNull {
                    it.data.opt(PAYLOAD_TYPE).list?.list
                }
                .flatten()
                .map { it.optMap() }
                .mapNotNull(Experiment::fromJson)
        } catch (ex: JsonException) {
            UALog.e(ex) { "Failed to parse experiments from remoteData payload" }
            return emptyList()
        }
    }
}

// Experiments filtering by message type
internal fun MessageCriteria.evaluate(info: MessageInfo): Boolean {
    return messageTypePredicate?.apply(JsonValue.wrap(info.messageType)) ?: false
}

// Calculate hash and define if it's withing the experiment bucket
internal fun AudienceSelector.isMatching(properties: Map<String, String?>): Boolean {
    return hash
        .generate(properties)
        ?.let { bucket.contains(it) }
        ?: false
}

internal fun AudienceHash.generate(properties: Map<String, String?>): Long? {
    if (!properties.containsKey(property.jsonValue)) {
        UALog.e { "can't find device property ${property.jsonValue}" }
    }

    val key = properties[property.jsonValue] ?: return null
    val value = overrides?.optionalField<String>(key) ?: key

    val hashFunction: HashFunction = when (algorithm) {
        HashAlgorithm.FARM -> FarmHashFingerprint64::fingerprint
    }

    return hashFunction.invoke("$prefix$value") % numberOfHashBuckets
}

private typealias HashFunction = (String) -> Long
private typealias ResolutionFunction = (Experiment, MessageInfo, Map<String, String?>) -> Boolean

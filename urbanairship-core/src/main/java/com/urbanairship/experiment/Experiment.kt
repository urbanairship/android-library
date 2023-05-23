package com.urbanairship.experiment

import androidx.annotation.RestrictTo
import com.urbanairship.Logger
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.optionalFieldConverted
import com.urbanairship.json.requireField

internal enum class ExperimentType(val jsonValue: String) {
    HOLDOUT_GROUP("Holdout");

    companion object {
        fun from(value: String): ExperimentType? {
            return values().firstOrNull { it.jsonValue == value }
        }
    }
}

internal enum class ResolutionType(val jsonValue: String) {
    STATIC("Static");

    companion object {
        fun from(value: String): ResolutionType? {
            return values().firstOrNull { it.jsonValue == value }
        }
    }
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ExperimentResult(
    public val channelId: String,
    public val contactId: String?,
    public val experimentId: String,
    public val reportingMetadata: JsonMap
)

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class MessageInfo(
    public val messageType: String
)

internal data class Experiment(
    val id: String,
    val type: ExperimentType,
    val resolutionType: ResolutionType,
    val lastUpdated: Long,
    val reportingMetadata: JsonMap,
    val audienceSelector: AudienceSelector,
    val exclusions: List<MessageCriteria>,
) {
    companion object {
        internal const val KEY_ID = "id"
        private const val KEY_TYPE = "experimentType"
        private const val KEY_LAST_UPDATED = "last_updated"
        private const val KEY_REPORTING_METADATA = "reporting_metadata"
        private const val KEY_RESOLUTION_TYPE = "type"
        private const val KEY_AUDIENCE_SELECTOR = "audience_selector"
        private const val KEY_HASH = "hash"
        private const val KEY_MESSAGE_EXCLUSION = "message_exclusions"
        private const val KEY_EVALUATION_OPTIONS = "evaluation_options"

        /**
         * Creates a `Experiment` object a [JsonMap].
         *
         * @param json A Remote Data payload containing configuration for an Experiment.
         *
         * @hide
         */
        internal fun fromJson(json: JsonMap): Experiment? {
            try {
                val type = json.optionalFieldConverted(KEY_TYPE, ExperimentType::from)
                    ?: return null
                val resolutionType = json.optionalFieldConverted(KEY_RESOLUTION_TYPE, ResolutionType::from)
                    ?: return null
                val audience = AudienceSelector.fromJson(
                    json
                        .require(KEY_AUDIENCE_SELECTOR)
                        .optMap()
                        .require(KEY_HASH)
                        .optMap()
                ) ?: return null

                val exclusions = json
                    .opt(KEY_MESSAGE_EXCLUSION)
                    .optList()
                    .map { it.optMap() }
                    .mapNotNull(MessageCriteria::fromJson)

                val options = json
                    .opt(KEY_EVALUATION_OPTIONS)
                    .optMap()
                    .let(EvaluationOptions::fromJson)

                return Experiment(
                    id = json.requireField(KEY_ID),
                    type = type,
                    resolutionType = resolutionType,
                    lastUpdated = json.requireField(KEY_LAST_UPDATED),
                    reportingMetadata = json.require(KEY_REPORTING_METADATA).optMap(),
                    audienceSelector = audience,
                    exclusions = exclusions
                )
            } catch (ex: JsonException) {
                Logger.e { "failed to parse Experiment from json $json" }
                return null
            }
        }
    }
}

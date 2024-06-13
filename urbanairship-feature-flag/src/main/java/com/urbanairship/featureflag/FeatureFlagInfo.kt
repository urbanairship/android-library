/* Copyright Airship and Contributors */

package com.urbanairship.featureflag

import android.net.Uri
import com.urbanairship.UALog
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.audience.AudienceSelector
import com.urbanairship.experiment.TimeCriteria
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import com.urbanairship.util.DateUtils
import java.text.ParseException

internal sealed class FeatureFlagVariables: JsonSerializable {
    data class Fixed(val data: JsonMap?) : FeatureFlagVariables() {

        @Throws(JsonException::class)
        override fun toJsonValue(): JsonValue {
            return jsonMapOf(
                KEY_TYPE to VariableType.FIXED.jsonValue,
                KEY_DATA to data
            ).toJsonValue()
        }
    }

    data class Variant(val variantVariables: List<VariablesVariant>) : FeatureFlagVariables() {

        @Throws(JsonException::class)
        override fun toJsonValue(): JsonValue {
            return jsonMapOf(
                KEY_TYPE to VariableType.VARIANTS.jsonValue,
                KEY_VARIANTS to variantVariables
            ).toJsonValue()
        }
    }

    companion object {

        private const val KEY_TYPE = "type"
        private const val KEY_VARIANTS = "variants"
        private const val KEY_DATA = "data"

        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): FeatureFlagVariables {
            val rawType: String = json.requireField(KEY_TYPE)
            val type =
                VariableType.entries.firstOrNull { it.jsonValue == rawType } ?: throw JsonException(
                    "can't parse type from $json"
                )

            return when (type) {
                VariableType.FIXED -> {
                    return Fixed(json.optionalField(KEY_DATA))
                }

                VariableType.VARIANTS -> {
                    Variant(json.requireField<JsonList>(KEY_VARIANTS).map {
                        VariablesVariant.fromJson(it.requireMap())
                    })
                }
            }
        }
    }
}

private enum class VariableType(val jsonValue: String) {
    FIXED("fixed"),
    VARIANTS("variant")
}

internal data class VariablesVariant(
    val id: String?,
    val selector: AudienceSelector?,
    val reportingMetadata: JsonMap?,
    val data: JsonMap?
) : JsonSerializable {

    internal companion object {
        private const val KEY_ID = "id"
        private const val KEY_REPORTING_METADATA = "reporting_metadata"
        private const val KEY_SELECTOR = "audience_selector"
        private const val KEY_DATA = "data"

        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): VariablesVariant {
            return VariablesVariant(
                id = json.requireField(KEY_ID),
                selector = AudienceSelector.fromJson(json.opt(KEY_SELECTOR)),
                reportingMetadata = json.requireField(KEY_REPORTING_METADATA),
                data = json.optionalField(KEY_DATA)
            )
        }
    }

    @Throws(JsonException::class)
    override fun toJsonValue(): JsonValue {
        return jsonMapOf(
            KEY_ID to id,
            KEY_SELECTOR to selector,
            KEY_REPORTING_METADATA to reportingMetadata,
            KEY_DATA to data
        ).toJsonValue()
    }
}

internal sealed class FeatureFlagPayload {
    internal data class DeferredPayload(val url: Uri) : FeatureFlagPayload()

    internal data class StaticPayload(val variables: FeatureFlagVariables? = null) : FeatureFlagPayload()
}

@OpenForTesting
internal data class FeatureFlagInfo(
    /**
     * Unique id of the flag, a UUID
     */
    val id: String,

    /**
     * Date of the object's creation
     */
    val created: Long,

    /**
     * Date of the last update to the flag definition
     */
    val lastUpdated: Long,

    /**
     * The flag name
     */
    val name: String,

    /**
     * The flag reporting metadata
     */
    val reportingContext: JsonMap,

    /**
     * Optional audience selector
     */
    val audience: AudienceSelector? = null,

    /**
     * Optional time span in which the flag should be active
     */
    val timeCriteria: TimeCriteria? = null,

    /**
     * Flag payload
     */
    val payload: FeatureFlagPayload,

    /**
     * Evaluation options.
     */
    val evaluationOptions: EvaluationOptions?  = null

) {
    internal companion object {
        private const val KEY_ID = "flag_id"
        private const val KEY_CREATED = "created"
        private const val KEY_LAST_UPDATED = "last_updated"
        private const val KEY_PAYLOAD = "flag"

        private const val KEY_REPORTING_METADATA = "reporting_metadata"
        private const val KEY_AUDIENCE_SELECTOR = "audience_selector"
        private const val KEY_TIME_CRITERIA = "time_criteria"
        private const val KEY_NAME = "name"
        private const val KEY_EVALUATION_OPTIONS = "evaluation_options"
        private const val KEY_URL = "url"
        private const val KEY_TYPE = "type"
        private const val KEY_DEFERRED = "deferred"
        private const val KEY_VARIABLES = "variables"

        /**
         * Creates a `FeatureFlagInfo` object a [JsonMap].
         *
         * @param json A Remote Data payload containing configuration for a FeatureFlagInfo.
         *
         * @hide
         */
        internal fun fromJson(json: JsonMap): FeatureFlagInfo? {
            try {
                val payload = json.require(KEY_PAYLOAD).optMap()
                val audience = payload.get(KEY_AUDIENCE_SELECTOR)?.let(AudienceSelector::fromJson)
                val payloadType =  payload.requireField<String>(KEY_TYPE).let { type ->
                    FeatureFlagPayloadType.entries.firstOrNull { it.jsonValue == type } ?: throw JsonException(
                        "Invalid feature flag payload type $type"
                    )
                }

                val parsedPayload = when (payloadType) {
                    FeatureFlagPayloadType.DEFERRED -> {
                        val url = Uri.parse(
                            payload.requireField<JsonMap>(KEY_DEFERRED).requireField(KEY_URL)
                        )
                        FeatureFlagPayload.DeferredPayload(url)
                    }

                    FeatureFlagPayloadType.STATIC -> {
                        val variables = payload.optionalField<JsonMap>(KEY_VARIABLES)?.let {
                            FeatureFlagVariables.fromJson(it)
                        }
                        FeatureFlagPayload.StaticPayload(variables)
                    }
                }

                return FeatureFlagInfo(
                    id = json.requireField(KEY_ID),
                    created = DateUtils.parseIso8601(json.requireField(KEY_CREATED)),
                    lastUpdated = DateUtils.parseIso8601(json.requireField(KEY_LAST_UPDATED)),
                    name = payload.requireField(KEY_NAME),
                    reportingContext = payload.require(KEY_REPORTING_METADATA).optMap(),
                    audience = audience,
                    timeCriteria = payload.opt(KEY_TIME_CRITERIA).map?.let { TimeCriteria.fromJson(it) },
                    payload = parsedPayload,
                    evaluationOptions = payload.opt(KEY_EVALUATION_OPTIONS).map?.let { EvaluationOptions.fromJson(it) }
                )
            } catch (ex: JsonException) {
                UALog.e { "failed to parse FeatureFlagInfo from json $json" }
                return null
            } catch (ex: ParseException) {
                UALog.e { "failed to parse FeatureFlagInfo from json $json" }
                return null
            }
        }
    }
}

private enum class FeatureFlagPayloadType(val jsonValue: String) {
    DEFERRED("deferred"),
    STATIC("static");
}

internal data class EvaluationOptions(
    val disallowStaleValues: Boolean?,
    val ttl: ULong?
) : JsonSerializable {

    companion object {

        private const val KEY_STALE_DATA_FLAG = "disallow_stale_value"
        private const val KEY_TTL = "ttl"

        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): EvaluationOptions {
            return EvaluationOptions(
                disallowStaleValues = json.optionalField(KEY_STALE_DATA_FLAG),
                ttl = json.optionalField(KEY_TTL)
            )
        }
    }

    @Throws(JsonException::class)
    override fun toJsonValue(): JsonValue {
        return jsonMapOf(
            KEY_STALE_DATA_FLAG to disallowStaleValues, KEY_TTL to ttl?.toLong()
        ).toJsonValue()
    }
}

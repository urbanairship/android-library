/* Copyright Airship and Contributors */

package com.urbanairship.featureflag

import android.content.Context
import android.net.Uri
import com.urbanairship.UALog
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.audience.AudienceSelector
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.experiment.ResolutionType
import com.urbanairship.experiment.TimeCriteria
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import com.urbanairship.util.DateUtils
import java.text.ParseException
import kotlin.jvm.Throws

internal enum class FeatureFlagVariablesType(val jsonValue: String) {
    FIXED("fixed"),
    VARIANTS("variant")
}

internal interface FeatureFlagPayload {
    suspend fun evaluateVariables(
        context: Context,
        newEvaluationDate: Long,
        infoProvider: DeviceInfoProvider
    ): VariablesVariant?
}

internal data class FeatureFlagVariables(
    val type: FeatureFlagVariablesType,
    val variants: List<VariablesVariant>
) {

    companion object {
        private const val KEY_TYPE = "type"
        private const val KEY_VARIANTS = "variants"

        private const val KEY_ID = "id"
        private const val KEY_AUDIENCE = "audience_selector"
        private const val KEY_REPORTING_METADATA = "reporting_metadata"
        private const val KEY_DATA = "data"

        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): FeatureFlagVariables {
            val rawType: String = json.requireField(KEY_TYPE)
            val type = FeatureFlagVariablesType.values().firstOrNull { it.jsonValue == rawType }
                ?: throw JsonException("can't parse type from $json")

            val flagVariants: List<VariablesVariant>
            if (type == FeatureFlagVariablesType.FIXED) {
                flagVariants = listOf(
                    VariablesVariant(id = null, selector = null, reportingMetadata = null, data = json.optionalField(KEY_DATA)))
            } else if (type == FeatureFlagVariablesType.VARIANTS) {
                flagVariants = json
                    .require(KEY_VARIANTS)
                    .optList()
                    .list
                    .map { it.optMap() }
                    .map {
                        VariablesVariant(
                            id = it.requireField(KEY_ID),
                            selector = AudienceSelector.fromJson(it.opt(KEY_AUDIENCE)),
                            reportingMetadata = it.requireField(KEY_REPORTING_METADATA),
                            data = it.optionalField(KEY_DATA)
                        )
                    }
            } else {
                throw JsonException("unsupported variants type $type")
            }

            return FeatureFlagVariables(type, flagVariants)
        }
    }

    suspend fun evaluateVariables(
        context: Context,
        newEvaluationDate: Long,
        infoProvider: DeviceInfoProvider
    ): VariablesVariant? {
        return when (type) {
            FeatureFlagVariablesType.FIXED -> {
                variants.firstOrNull()
            }
            FeatureFlagVariablesType.VARIANTS -> {
                variants.firstOrNull { it.selector?.evaluate(context, newEvaluationDate, infoProvider) ?: false }
            }
        }
    }
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

        internal val empty = VariablesVariant(null, null, null, null)
    }

    override fun toJsonValue(): JsonValue {
        return jsonMapOf(
            KEY_ID to id,
            KEY_SELECTOR to selector,
            KEY_REPORTING_METADATA to reportingMetadata,
            KEY_DATA to data
        ).toJsonValue()
    }
}

@OpenForTesting
internal class FeatureFlagInfo(
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
    val audience: AudienceSelector?,

    /**
     * Optional time span in which the flag should be active
     */
    val timeCriteria: TimeCriteria?,

    /**
     * Flag payload
     */
    val payload: FeatureFlagPayload,

    /**
     * Evaluation options.
     */
    val evaluationOptions: EvaluationOptions?

) {
    internal companion object {
        private const val KEY_ID = "flag_id"
        private const val KEY_CREATED = "created"
        private const val KEY_LAST_UPDATED = "last_updated"
        private const val KEY_PAYLOAD = "flag"

        private const val KEY_TYPE = "type"
        private const val KEY_VARIABLES = "variables"
        private const val KEY_DEFERRED = "deferred"
        private const val KEY_REPORTING_METADATA = "reporting_metadata"
        private const val KEY_AUDIENCE_SELECTOR = "audience_selector"
        private const val KEY_TIME_CRITERIA = "time_criteria"
        private const val KEY_NAME = "name"
        private const val KEY_EVALUATION_OPTIONS = "evaluation_options"

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

                return FeatureFlagInfo(
                    id = json.requireField(KEY_ID),
                    created = DateUtils.parseIso8601(json.requireField(KEY_CREATED)),
                    lastUpdated = DateUtils.parseIso8601(json.requireField(KEY_LAST_UPDATED)),
                    name = payload.requireField(KEY_NAME),
                    reportingContext = payload.require(KEY_REPORTING_METADATA).optMap(),
                    audience = audience,
                    timeCriteria = TimeCriteria.fromJson(payload.opt(KEY_TIME_CRITERIA).optMap()),
                    payload = parsePayload(payload),
                    evaluationOptions = EvaluationOptions.fromJson(payload.opt(KEY_EVALUATION_OPTIONS).optMap())
                )
            } catch (ex: JsonException) {
                UALog.e { "failed to parse FeatureFlagInfo from json $json" }
                return null
            } catch (ex: ParseException) {
                UALog.e { "failed to parse FeatureFlagInfo from json $json" }
                return null
            }
        }

        @Throws(JsonException::class)
        private fun parsePayload(json: JsonMap): FeatureFlagPayload {
            val type = ResolutionType.from(json.requireField(KEY_TYPE))
                ?: throw JsonException("can't parse type from $json")

            return when (type) {
                ResolutionType.DEFERRED -> DeferredPayload.fromJson(json.opt(KEY_DEFERRED).optMap())
                ResolutionType.STATIC -> StaticPayload.fromJson(json.opt(KEY_VARIABLES).optMap())
            }
        }
    }
}

internal class DeferredPayload(
    val url: Uri,
) : FeatureFlagPayload {
    companion object {
        private const val KEY_URL = "url"

        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): DeferredPayload {

            return DeferredPayload(
                url = Uri.parse(json.require(KEY_URL).requireString()),
            )
        }
    }

    override suspend fun evaluateVariables(
        context: Context,
        newEvaluationDate: Long,
        infoProvider: DeviceInfoProvider
    ): VariablesVariant? {
        UALog.e { "method not implemented" }
        return null
    }
}

internal class StaticPayload(
    private val variableVariants: FeatureFlagVariables
) : FeatureFlagPayload {

    companion object {

        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): StaticPayload {
            if (json.isEmpty) {
                val variables = FeatureFlagVariables(
                    type = FeatureFlagVariablesType.FIXED, variants = listOf(VariablesVariant.empty)
                )
                return StaticPayload(variables)
            }

            return StaticPayload(FeatureFlagVariables.fromJson(json))
        }
    }

    override suspend fun evaluateVariables(
        context: Context,
        newEvaluationDate: Long,
        infoProvider: DeviceInfoProvider
    ): VariablesVariant? {
        return variableVariants.evaluateVariables(context, newEvaluationDate, infoProvider)
    }
}

internal class EvaluationOptions(
    val disallowStaleValues: Boolean?,
    val ttl: ULong?
) : JsonSerializable {

    companion object {
        private const val KEY_STALE_DATA_FLAG = "disallow_stale_value"
        private const val KEY_TTL = "ttl"

        fun fromJson(json: JsonMap): EvaluationOptions? {
            return EvaluationOptions(
                    disallowStaleValues = json.optionalField(KEY_STALE_DATA_FLAG),
                    ttl = json.optionalField(KEY_TTL)
            )
        }
    }

    override fun toJsonValue(): JsonValue {
        return jsonMapOf(
            KEY_STALE_DATA_FLAG to disallowStaleValues,
            KEY_TTL to ttl?.toLong()
        ).toJsonValue()
    }
}

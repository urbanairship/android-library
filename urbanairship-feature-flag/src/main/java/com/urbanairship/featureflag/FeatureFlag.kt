/* Copyright Airship and Contributors */

package com.urbanairship.featureflag

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonListOf
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField

/**
 * Airship Feature flag.
 */
public class FeatureFlag private constructor(

    /**
     * Flag name. Will be empty if the flag was created through the deprecated constructor.
     */
    public val name: String,

    /**
     * Indicates whether the device is eligible or not for the flag.
     */
    public val isEligible: Boolean,

    /**
     * Indicates whether the flag exists in the current flag listing or not
     */
    public val exists: Boolean,

    /**
     * Optional reporting info. Will be `null` if the flag is created through the
     * deprecated constructor or if it does not exist.
     */
    internal val reportingInfo: ReportingInfo?,

    /**
     * Optional variables associated with the flag
     */
    public val variables: JsonMap?,
) : JsonSerializable {

    /**
     * Public constructor.
     *
     * @deprecated Applications should not create a flag directly, instead request a flag
     *      via [FeatureFlagManager.flag] or [FeatureFlagManager.flagAsPendingResult].
     */
    @Deprecated("Applications should not create a flag directly, instead request a flag " +
            "via FeatureFlagManager.flag or FeatureFlagManager.flagAsPendingResult")
    public constructor(isEligible: Boolean, exists: Boolean, variables: JsonMap?) :
            this("", isEligible, exists, null, variables)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FeatureFlag

        if (name != other.name) return false
        if (isEligible != other.isEligible) return false
        if (exists != other.exists) return false
        if (variables != other.variables) return false
        if (reportingInfo != other.reportingInfo) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + isEligible.hashCode()
        result = 31 * result + exists.hashCode()
        result = 31 * result + (variables?.hashCode() ?: 0)
        result = 31 * result + (reportingInfo?.hashCode() ?: 0)
        return result
    }

    @Throws(JsonException::class)
    override fun toJsonValue(): JsonValue = jsonMapOf(
        KEY_NAME to name,
        KEY_EXISTS to exists,
        KEY_IS_ELIGIBLE to isEligible,
        KEY_VARIABLES to variables,
        KEY_REPORTING_INFO to reportingInfo
    ).toJsonValue()

    internal fun copyWith(
        isEligible: Boolean? = null,
        variables: JsonMap? = null): FeatureFlag {
        return FeatureFlag(
            name = this.name,
            isEligible = isEligible ?: this.isEligible,
            exists = this.exists,
            reportingInfo = this.reportingInfo,
            variables = variables ?: this.variables
        )
    }

    override fun toString(): String {
        return "FeatureFlag(name='$name', isEligible=$isEligible, exists=$exists, reportingInfo=$reportingInfo, variables=$variables)"
    }

    public companion object {

        @JvmSynthetic
        internal fun createMissingFlag(
            name: String
        ): FeatureFlag = FeatureFlag(
            name = name,
            isEligible = false,
            exists = false,
            variables = null,
            reportingInfo = null
        )

        @JvmSynthetic
        internal fun createFlag(
            name: String,
            isEligible: Boolean,
            reportingInfo: ReportingInfo,
            variables: JsonMap? = null,
        ): FeatureFlag = FeatureFlag(
            name = name,
            isEligible = isEligible,
            exists = true,
            variables = variables,
            reportingInfo = reportingInfo
        )

        private const val KEY_NAME = "name"
        private const val KEY_EXISTS = "exists"
        private const val KEY_IS_ELIGIBLE = "is_eligible"
        private const val KEY_VARIABLES = "variables"
        private const val KEY_REPORTING_INFO = "_reporting_info"

        /**
         * Parses a `JsonValue` as a `FeatureFlag`.
         * @throws `JsonException`
         */
        @JvmStatic
        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): FeatureFlag {
            return FeatureFlag(
                name = json.requireMap().requireField(KEY_NAME),
                isEligible = json.requireMap().requireField(KEY_IS_ELIGIBLE),
                exists = json.requireMap().requireField(KEY_EXISTS),
                reportingInfo = json.requireMap().get(KEY_REPORTING_INFO)?.let {
                        ReportingInfo.fromJson(it)
                },
                variables = json.requireMap().optionalField(KEY_VARIABLES),
            )
        }
    }

    internal data class ReportingInfo(
        var reportingMetadata: JsonMap,
        var supersededReportingMetadata: List<JsonMap>? = null,
        val channelId: String? = null,
        val contactId: String? = null
    ) : JsonSerializable {

        companion object {
            private const val KEY_REPORTING_METADATA = "reporting_metadata"
            private const val KEY_SUPERSEDED_REPORTING_METADATA = "superseded_reporting_metadata"
            private const val KEY_CHANNEL_ID = "channel_id"
            private const val KEY_CONTACT_ID = "contact_id"

            @Throws(JsonException::class)
            fun fromJson(json: JsonValue): ReportingInfo {
                return ReportingInfo(
                    reportingMetadata = json.requireMap().requireField(KEY_REPORTING_METADATA),
                    channelId = json.requireMap().requireField(KEY_CHANNEL_ID),
                    contactId = json.requireMap().requireField(KEY_CONTACT_ID)
                )
            }
        }

        fun addSuperseded(metadata: JsonMap?) {
            val value = metadata ?: return
            val mutable = (supersededReportingMetadata ?: emptyList()).toMutableList()

            mutable.add(value)

            supersededReportingMetadata = mutable
        }

        @Throws(JsonException::class)
        override fun toJsonValue() = jsonMapOf(
            KEY_REPORTING_METADATA to reportingMetadata,
            KEY_CHANNEL_ID to channelId,
            KEY_CONTACT_ID to contactId,
            KEY_SUPERSEDED_REPORTING_METADATA to supersededReportingMetadata
        ).toJsonValue()
    }
}

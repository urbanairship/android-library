/* Copyright Airship and Contributors */

package com.urbanairship.audience

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class AirshipDeviceAudienceResult(
    val isMatch: Boolean,
    val reportingMetadata: List<JsonValue>? = null
) : JsonSerializable {

    internal fun negate(): AirshipDeviceAudienceResult = AirshipDeviceAudienceResult(!isMatch, reportingMetadata)

    public companion object {
        public val match: AirshipDeviceAudienceResult = AirshipDeviceAudienceResult(true)
        public val miss: AirshipDeviceAudienceResult = AirshipDeviceAudienceResult(false)

        private const val IS_MATCH = "is_match"
        private const val REPORTING_METADATA = "reporting_metadata"

        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): AirshipDeviceAudienceResult {
            val content = value.requireMap()

            return AirshipDeviceAudienceResult(
                isMatch = content.requireField(IS_MATCH),
                reportingMetadata = content.get(REPORTING_METADATA)?.requireList()?.list
            )
        }

        internal fun reduced(results: List<AirshipDeviceAudienceResult>): AirshipDeviceAudienceResult {
            var isMatch = true
            var metadata: MutableList<JsonValue>? = null

            results.forEach { item ->
                isMatch = isMatch && item.isMatch
                item.reportingMetadata?.let { partial ->
                    if (metadata == null) {
                        metadata = mutableListOf()
                    }
                    metadata?.addAll(partial)
                }
            }

            return AirshipDeviceAudienceResult(isMatch, metadata)
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        IS_MATCH to isMatch,
        REPORTING_METADATA to reportingMetadata
    ).toJsonValue()
}

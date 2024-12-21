/* Copyright Airship and Contributors */

package com.urbanairship.audience

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField

internal data class AudienceSticky(
    /**
     * The sticky ID. The SDK will cache the result and reporting_metadata for the
     * hash under the id and hash_identifier. If either changes, the hash is recalculated.
     */
    val id: String,

    /**
     * Reporting metadata that will be cached with the id.
     */
    val reportingMetadata: JsonValue?,

    /**
     * How long to hold onto the result for since the last access in milliseconds.
     */
    val lastAccessTtl: Long
) : JsonSerializable {

    companion object {
        private const val ID = "id"
        private const val REPORTING_METADATA = "reporting_metadata"
        private const val LAST_ACCESS_TTL = "last_access_ttl"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): AudienceSticky {
            val content = value.requireMap()
            return AudienceSticky(
                id = content.requireField(ID),
                reportingMetadata = content.get(REPORTING_METADATA),
                lastAccessTtl = content.requireField(LAST_ACCESS_TTL)
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        ID to id,
        REPORTING_METADATA to reportingMetadata,
        LAST_ACCESS_TTL to lastAccessTtl
    ).toJsonValue()
}

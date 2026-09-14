/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField

/**
 * Ledger configuration parsed off a schedule's `ledger_config` payload.
 *
 * [sharedId] is stamped onto every event the schedule records, at record time,
 * and is never rewritten, so events stay with the group they were recorded
 * under even if the schedule's config later changes. Recording is
 * unconditional — a schedule with [sharedId] set tags its events with it
 * regardless of whether it reads that group's history back itself. Whether it
 * does is decided by [LimitConfig.includeSharedEvents], not by this field.
 */
internal data class LedgerConfig(
    val sharedId: String? = null
) : JsonSerializable {

    internal companion object {
        private const val SHARED_ID = "shared_id"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): LedgerConfig {
            return LedgerConfig(
                sharedId = value.requireMap().optionalField(SHARED_ID)
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        SHARED_ID to sharedId
    ).toJsonValue()
}

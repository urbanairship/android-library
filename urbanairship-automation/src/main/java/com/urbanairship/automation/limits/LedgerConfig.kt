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
 * The [sharedId] pools a schedule's ledger events with other schedules that
 * share the same group, so a group-wide limit can be evaluated across all of
 * them. It is stamped onto every event the schedule records at record time and
 * is never rewritten, so events stay with the group they were recorded under
 * even if the schedule's config later changes.
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

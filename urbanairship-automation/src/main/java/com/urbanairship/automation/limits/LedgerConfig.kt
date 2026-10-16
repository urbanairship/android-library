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
 * The [sharedIds] name the groups a schedule records under. More than one lets
 * it belong to several pools at once — combining messages that already pool
 * separately into one experiment without either losing its existing group.
 *
 * They are stamped onto every event the schedule records, at record time, and
 * are never rewritten, so events stay with the groups they were recorded under
 * even if the schedule's config later changes. Recording is unconditional — a
 * schedule with [sharedIds] set tags its events with them regardless of
 * whether it reads those groups' history back itself. Whether it does is
 * decided by [LimitConfig] (`type: 'shared'` vs `'self'`), not by this field.
 *
 * [sharedIds] is always canonical (non-empty, sorted, de-duplicated, or
 * null), whichever entry point built the config, so two configs naming the
 * same groups compare equal however the backend ordered them. That keeps a
 * re-fetch which only reorders the array from reading as a changed schedule
 * and invalidating a prepared execution. The primary constructor is private
 * so this holds for every path; construct through the companion's `invoke`.
 *
 * Order matters here because this engine detects a changed schedule by
 * comparing the definition itself. iOS compares a modification timestamp that
 * every upsert bumps, so canonical order makes no difference there.
 */
internal data class LedgerConfig private constructor(
    val sharedIds: List<String>? = null
) : JsonSerializable {

    internal companion object {
        private const val SHARED_IDS = "shared_ids"
        private const val LEGACY_SHARED_ID = "shared_id"

        /** Canonicalizes [sharedIds] before constructing; see the class doc. */
        operator fun invoke(sharedIds: List<String>? = null): LedgerConfig =
            LedgerConfig(sharedIds = LedgerSharedIds.canonicalOrNull(sharedIds))

        /**
         * Accepts the single-ID `shared_id` this field replaced; `shared_ids`
         * wins when both are present. See
         * [LedgerSharedIds.decodeWithLegacyFallback].
         */
        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): LedgerConfig {
            val content = value.requireMap()
            val sharedIds = LedgerSharedIds.decodeWithLegacyFallback(
                content, SHARED_IDS, LEGACY_SHARED_ID
            )
            // Binds to the private primary constructor, not `invoke`: a
            // visible constructor wins, and it is visible inside the
            // companion. Hence canonicalizing here rather than relying on the
            // factory to do it.
            return LedgerConfig(sharedIds = LedgerSharedIds.canonicalOrNull(sharedIds))
        }
    }

    /** Only ever writes the current shape; the legacy key is read-only. */
    override fun toJsonValue(): JsonValue = jsonMapOf(
        SHARED_IDS to sharedIds
    ).toJsonValue()
}

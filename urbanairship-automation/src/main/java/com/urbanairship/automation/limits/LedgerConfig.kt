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
 * [sharedIds] is always in canonical form (non-empty, sorted, de-duplicated,
 * or null) regardless of how a [LedgerConfig] is built — so two configs
 * naming the same set of groups compare equal regardless of the order the
 * backend happened to list them in, which is what lets
 * [com.urbanairship.automation.AutomationSchedule] equality treat an
 * unchanged schedule as unchanged across a re-fetch that merely reorders this
 * array. The primary constructor is private so this holds for every
 * construction path, not only [fromJson] — the public entry point is the
 * companion's `invoke`, which canonicalizes before delegating to it.
 *
 * That canonical-at-the-boundary rule is a deliberate divergence from iOS,
 * which holds the decoded array verbatim here and canonicalizes only in the
 * derived `AutomationSchedule.ledgerSharedIDs`. Three behaviours differ as a
 * result: re-encoding a payload normalizes the array instead of preserving it,
 * an explicit `[]` is dropped rather than round-tripped, and a re-fetch that
 * only reorders `shared_ids` compares equal here where iOS treats it as a
 * changed schedule and invalidates a prepared execution. The last of those is
 * the reason for the divergence — spurious invalidation cancels a prepared
 * message over a difference that means nothing — and the same normalization is
 * applied to [com.urbanairship.automation.engine.PreparedScheduleInfo] for
 * consistency. Worth reconciling with iOS rather than leaving the two
 * platforms to behave differently on the same payload.
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
         * Accepts the single-ID `shared_id` this field replaced, so a payload
         * written against the older shape still pools. `shared_ids` wins when
         * both are present.
         *
         * Worth the branch because the failure it prevents is silent and
         * wrong in the expensive direction: an unrecognized key parses to no
         * shared group at all, which is indistinguishable from "pooling was
         * never configured," and every variant of an experiment would then
         * spend its own full budget instead of the one they were meant to
         * share.
         */
        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): LedgerConfig {
            val content = value.requireMap()
            val current = content.get(SHARED_IDS)?.requireList()?.map { it.requireString() }
            val sharedIds = current
                ?: content.optionalField<String>(LEGACY_SHARED_ID)?.let { listOf(it) }
            // Binds to the private primary constructor rather than the
            // `invoke` factory above: a visible constructor always wins over a
            // companion `invoke`, and inside the companion the constructor is
            // visible. Hence the repeated `canonicalOrNull` — the constructor
            // does not canonicalize, its callers do, and here that is the
            // point, since this is the one call site that also has to fold in
            // the legacy key first.
            return LedgerConfig(sharedIds = LedgerSharedIds.canonicalOrNull(sharedIds))
        }
    }

    /** Only ever writes the current shape; the legacy key is read-only. */
    override fun toJsonValue(): JsonValue = jsonMapOf(
        SHARED_IDS to sharedIds
    ).toJsonValue()
}

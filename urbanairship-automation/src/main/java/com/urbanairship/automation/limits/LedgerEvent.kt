/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireEpochMillis
import com.urbanairship.json.requireField
import java.time.Instant

/**
 * Coarse event types tracked by the ledger. Every schedule records events
 * automatically: `triggered` when an execution-causing trigger reaches its
 * goal, and `execution` when that attempt resolves to a budget-consuming
 * outcome.
 *
 * This axis is deliberately coarse so counting stays forward-compatible: a
 * limit counts `execution` events regardless of their [LedgerExecutionResult].
 */
internal enum class LedgerEventType(val json: String) : JsonSerializable {
    TRIGGERED("triggered"),
    EXECUTION("execution");

    override fun toJsonValue(): JsonValue = JsonValue.wrap(json)

    companion object {
        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): LedgerEventType {
            val content = value.requireString()
            return entries.firstOrNull { it.json == content }
                ?: throw JsonException("Invalid ledger event type $content")
        }
    }
}

/**
 * How an `execution` event resolved. This is the fine axis: it never affects
 * whether an execution is counted, only which executions an exclusion rule
 * can subtract.
 *
 * Modelled as a sealed class rather than an enum so a result written by a
 * newer SDK still parses. Counting must not depend on recognizing the result:
 * an event that fails to parse is skipped on read, which would drop it from
 * the tally and let a schedule that had spent its budget execute again — the
 * opposite of erring toward showing less.
 */
internal sealed class LedgerExecutionResult(internal val json: String) : JsonSerializable {
    /** The schedule did its thing: the scene was displayed or the actions ran. */
    data object SUCCEEDED : LedgerExecutionResult("succeeded")

    /**
     * The user reached the very last mile before display (trigger fired,
     * audience and frequency checks passed, assets loaded) but the resolved
     * outcome was "no message." Everything except display or actions
     * occurred, so it counts toward the limit like a real execution. Emitted
     * by the global holdout mechanism (holdout experiment groups /
     * `bypass_holdout_groups`), and equally by an Experiment Groups variant
     * experiment whose resolved hash bucket lands in its own no-message arm —
     * the two are indistinguishable in their effect on the ledger, only in
     * why they occurred.
     */
    data object HOLDOUT : LedgerExecutionResult("holdout")

    /**
     * Reached the same last mile as [HOLDOUT], but the resolved hash bucket
     * landed in neither this schedule's own arm nor its holdout arm — i.e.
     * some sibling schedule's arm owns it. Not a holdout: the user wasn't
     * assigned "no message," just not this schedule's message. This is what
     * lets a schedule whose own trigger fired, and who would otherwise have
     * executed, still be counted as a known member of the experiment
     * audience. Counts toward the limit by default, same as any other
     * execution.
     */
    data object VARIANT_MISS : LedgerExecutionResult("variant_miss")

    /** The audience check failed with a budget-consuming miss behavior. */
    data object AUDIENCE_MISS : LedgerExecutionResult("audience_miss")

    /** Synthesized from a pre-ledger execution count during migration. */
    data object BACKFILL : LedgerExecutionResult("backfill")

    /**
     * A result this SDK version does not recognize, carrying the value it was
     * recorded with so a rewrite round-trips it rather than flattening it to a
     * placeholder. The event counts toward its limit like any other execution,
     * and matches no exclusion rule, so it errs toward showing less.
     */
    data class Unknown(val rawValue: String) : LedgerExecutionResult(rawValue)

    override fun toJsonValue(): JsonValue = JsonValue.wrap(json)

    internal companion object {
        /**
         * Every result this SDK version recognizes.
         *
         * Deliberately lazy: building this list eagerly would read the nested
         * objects from the companion's own initializer, which runs while the
         * sealed class is still initializing, and each element would come back
         * null.
         */
        internal val known: List<LedgerExecutionResult> by lazy {
            listOf(SUCCEEDED, HOLDOUT, VARIANT_MISS, AUDIENCE_MISS, BACKFILL)
        }

        /**
         * Never rejects an unrecognized result: it becomes [Unknown] so the
         * event still parses and still counts. Only a non-string value, which
         * is malformed rather than merely newer, throws.
         */
        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): LedgerExecutionResult {
            val content = value.requireString()
            return known.firstOrNull { it.json == content } ?: Unknown(content)
        }
    }
}

/**
 * The scope an event was recorded under. An event is always recorded under
 * its schedule's ID, and additionally under every shared group ID the
 * recording schedule had in `ledger_config.shared_ids` at record time.
 */
internal sealed class LedgerScope {
    /** Events recorded by a specific schedule (matched on `scheduleId`). */
    data class Schedule(val scheduleId: String) : LedgerScope()

    /**
     * Events recorded under a specific shared group (matched against an
     * event's `sharedIds`).
     */
    data class Shared(val sharedId: String) : LedgerScope()
}

/**
 * Canonicalization for the shared group IDs carried by a config or an event.
 *
 * A schedule's `ledger_config.shared_ids` is an unordered set in intent but
 * arrives as a JSON array, so it is reduced to one canonical form — empties
 * dropped, duplicates removed, sorted — before it is stamped onto an event or
 * used for matching. Canonical form matters beyond tidiness: it makes two
 * events that belong to the same groups compare and hash equal, which is what
 * lets [LedgerCompactor] merge them, and it gives every caller of
 * [LedgerGroupReservations] the same lock ordering.
 */
internal object LedgerSharedIds {

    /**
     * The shared IDs in [content], reading [currentKey] and falling back to
     * the single-ID [legacyKey] it replaced. Null when neither is present.
     *
     * Not canonicalized: where that happens differs by call site, since an
     * event defers it to the storage-write boundary while a config does it on
     * parse. An explicitly empty array means "no pooling" and does not fall
     * through to the legacy key.
     *
     * The fallback itself is what matters. A payload or persisted row written
     * by an older build decodes to no shared group at all without it, which is
     * indistinguishable from pooling never having been configured — every
     * variant of an experiment then spends its own full budget.
     *
     * @param content The decoded object to read.
     * @param currentKey The array key.
     * @param legacyKey The single-string key it replaced.
     * @return The IDs as written, or null if neither key is present.
     */
    @Throws(JsonException::class)
    fun decodeWithLegacyFallback(
        content: JsonMap,
        currentKey: String,
        legacyKey: String
    ): List<String>? = content.get(currentKey)?.requireList()?.map { it.requireString() }
        ?: content.optionalField<String>(legacyKey)?.let { listOf(it) }

    /**
     * The canonical form of [ids]: non-empty, de-duplicated, sorted. Empty
     * when there are no usable IDs.
     *
     * Sorts and compares by UTF-16 code unit, which is what `TreeSet` gives.
     * iOS sorts the same set with Swift's `<`, which compares by Unicode
     * canonical equivalence, so the two platforms can disagree on a group ID
     * outside the BMP (order) or on two canonically-equivalent spellings of
     * one ID (Android keeps both, iOS folds them into one). Left as-is rather
     * than normalizing: group IDs are backend-issued identifiers, matched
     * everywhere else in this SDK — and in the database — by exact string
     * equality, and making this one function fold equivalents would put it out
     * of step with every place the result is then compared.
     */
    fun canonical(ids: List<String>?): List<String> {
        if (ids.isNullOrEmpty()) {
            return emptyList()
        }
        return ids.filter { it.isNotEmpty() }.toSortedSet().toList()
    }

    /**
     * The canonical form of [ids], or null when empty — for optional payload
     * fields that should be omitted from JSON rather than encoded as `[]`.
     */
    fun canonicalOrNull(ids: List<String>?): List<String>? = canonical(ids).ifEmpty { null }
}

/**
 * A single event recorded by the ledger.
 *
 * Every event carries the scopes it was recorded under: `scheduleId` always,
 * and `sharedIds` (the `ledger_config.shared_ids` active at record time) when
 * the recording schedule had any. No ID is ever rewritten, so an event stays
 * with the scopes it was recorded under even after the schedule changes its
 * config.
 *
 * An event is eligible for a schedule's limit evaluation when any scope ID
 * matches one of the schedule's ledger IDs: its `scheduleId` equals the
 * schedule's own ID, or its `sharedIds` intersect the schedule's current
 * shared ledger IDs.
 */
internal sealed class LedgerEvent : JsonSerializable {

    /** The coarse type of the event. */
    abstract val type: LedgerEventType

    /** ID of the schedule that recorded the event. */
    abstract val scheduleId: String

    /**
     * Shared group IDs the recording schedule had at record time, if any.
     * Canonical form: non-empty, de-duplicated, sorted.
     */
    abstract val sharedIds: List<String>?

    /**
     * ID of the execution-causing trigger, if known. A plain attribute for
     * matching and reporting only; never a recording scope key.
     */
    abstract val triggerId: String?

    /**
     * `variant_audience.id` the recording schedule had at record time, if any.
     * A plain attribute for matching only; never a recording scope key.
     */
    abstract val variantAudienceId: String?

    /** When the event was recorded. */
    abstract val timestamp: Instant

    /** Number of occurrences this event represents. If null, 1. */
    abstract val count: Int?

    /**
     * Number of occurrences this event represents, treating a null [count] as
     * 1. Rows with a count greater than one (backfill, compacted history) use
     * [timestamp] as an upper bound: every represented occurrence happened at
     * or before it.
     */
    val effectiveCount: Int
        get() = count ?: 1

    /** Shared group IDs the recording schedule had at record time. Empty when it had none. */
    val effectiveSharedIds: List<String>
        get() = sharedIds ?: emptyList()

    /**
     * A copy of this event with [sharedIds] replaced by their canonical form.
     *
     * Used at the storage boundary so every writer — direct recording and a
     * compaction rewrite alike — persists the same de-duplicated, sorted rows
     * and a matching encoded body, rather than depending on every caller (and
     * every future one) canonicalizing before it gets there.
     */
    fun canonicalizingSharedIds(): LedgerEvent = when (this) {
        is Triggered -> copy(sharedIds = LedgerSharedIds.canonicalOrNull(sharedIds))
        is Execution -> copy(sharedIds = LedgerSharedIds.canonicalOrNull(sharedIds))
    }

    /** Recorded when an execution-causing trigger reaches its goal. */
    data class Triggered(
        override val scheduleId: String,
        override val sharedIds: List<String>? = null,
        override val triggerId: String? = null,
        override val variantAudienceId: String? = null,
        override val timestamp: Instant,
        override val count: Int? = null
    ) : LedgerEvent() {
        override val type: LedgerEventType = LedgerEventType.TRIGGERED

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            SCHEDULE_ID to scheduleId,
            SHARED_IDS to sharedIds,
            TRIGGER_ID to triggerId,
            VARIANT_AUDIENCE_ID to variantAudienceId,
            TIMESTAMP to timestamp.toEpochMilli(),
            COUNT to count
        ).toJsonValue()
    }

    /**
     * Recorded when an attempt resolves to a budget-consuming outcome. Every
     * execution counts toward the limit regardless of its
     * [LedgerExecutionResult]; the result only governs which executions an
     * exclusion rule can subtract.
     *
     * A [LedgerExecutionResult.BACKFILL] result carries the pre-ledger
     * execution count in `count`: it has no `triggerId` and its `sharedIds`
     * are always null (recorded only under the schedule ID, so legacy history
     * never pollutes a group's pooled tally), and its timestamp reflects when
     * migration occurred, not when the original executions happened.
     */
    data class Execution(
        override val scheduleId: String,
        override val sharedIds: List<String>? = null,
        override val triggerId: String? = null,
        override val variantAudienceId: String? = null,
        override val timestamp: Instant,
        override val count: Int? = null,
        /** How the execution resolved. */
        val result: LedgerExecutionResult,
        /**
         * True if this outcome also cancelled the schedule. If null, false.
         */
        val cancel: Boolean? = null
    ) : LedgerEvent() {
        override val type: LedgerEventType = LedgerEventType.EXECUTION

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            SCHEDULE_ID to scheduleId,
            SHARED_IDS to sharedIds,
            TRIGGER_ID to triggerId,
            VARIANT_AUDIENCE_ID to variantAudienceId,
            TIMESTAMP to timestamp.toEpochMilli(),
            COUNT to count,
            RESULT to result,
            CANCEL to cancel
        ).toJsonValue()
    }

    internal companion object {
        private const val TYPE = "type"
        private const val SCHEDULE_ID = "schedule_id"
        private const val SHARED_IDS = "shared_ids"
        /**
         * Read-only: the single-ID key [SHARED_IDS] replaced. A device upgrading
         * from a pre-multi-group SDK has existing rows whose persisted `body`
         * still uses this key — the storage migration only promotes the old
         * `sharedId` SQL column into the new join table, it does not rewrite
         * this JSON, so decoding still has to understand it.
         */
        private const val LEGACY_SHARED_ID = "shared_id"
        private const val TRIGGER_ID = "trigger_id"
        private const val VARIANT_AUDIENCE_ID = "variant_audience_id"
        private const val TIMESTAMP = "timestamp"
        private const val COUNT = "count"
        private const val RESULT = "result"
        private const val CANCEL = "cancel"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): LedgerEvent {
            val content = value.requireMap()
            val sharedIds = LedgerSharedIds.decodeWithLegacyFallback(
                content, SHARED_IDS, LEGACY_SHARED_ID
            )
            return when (LedgerEventType.fromJson(content.require(TYPE))) {
                LedgerEventType.TRIGGERED -> Triggered(
                    scheduleId = content.requireField(SCHEDULE_ID),
                    sharedIds = sharedIds,
                    triggerId = content.optionalField(TRIGGER_ID),
                    variantAudienceId = content.optionalField(VARIANT_AUDIENCE_ID),
                    timestamp = content.requireEpochMillis(TIMESTAMP),
                    count = content.optionalField(COUNT)
                )

                LedgerEventType.EXECUTION -> Execution(
                    scheduleId = content.requireField(SCHEDULE_ID),
                    sharedIds = sharedIds,
                    triggerId = content.optionalField(TRIGGER_ID),
                    variantAudienceId = content.optionalField(VARIANT_AUDIENCE_ID),
                    timestamp = content.requireEpochMillis(TIMESTAMP),
                    count = content.optionalField(COUNT),
                    result = LedgerExecutionResult.fromJson(content.require(RESULT)),
                    cancel = content.optionalField(CANCEL)
                )
            }
        }
    }
}

/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import com.urbanairship.json.JsonException
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
 * its schedule's ID, and additionally under a shared group ID when the
 * recording schedule had a `ledger_config.shared_id` at record time.
 */
internal sealed class LedgerScope {
    /** Events recorded by a specific schedule (matched on `scheduleId`). */
    data class Schedule(val scheduleId: String) : LedgerScope()

    /** Events recorded under a specific shared group (matched on `sharedId`). */
    data class Shared(val sharedId: String) : LedgerScope()
}

/**
 * A single event recorded by the ledger.
 *
 * Every event carries the scope it was recorded under: `scheduleId` always,
 * and `sharedId` (the `ledger_config.shared_id` active at record time) when
 * the recording schedule had one. Neither ID is ever rewritten, so an event
 * stays with the scopes it was recorded under even after the schedule changes
 * its config.
 *
 * An event is eligible for a schedule's limit evaluation when either scope ID
 * matches one of the schedule's ledger IDs: its `scheduleId` equals the
 * schedule's own ID, or its `sharedId` equals the schedule's current shared
 * ledger ID.
 */
internal sealed class LedgerEvent : JsonSerializable {

    /** The coarse type of the event. */
    abstract val type: LedgerEventType

    /** ID of the schedule that recorded the event. */
    abstract val scheduleId: String

    /** Shared group ID the recording schedule had at record time, if any. */
    abstract val sharedId: String?

    /**
     * ID of the execution-causing trigger, if known. A plain attribute for
     * matching and reporting only; never a recording scope key.
     */
    abstract val triggerId: String?

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

    /** Recorded when an execution-causing trigger reaches its goal. */
    data class Triggered(
        override val scheduleId: String,
        override val sharedId: String? = null,
        override val triggerId: String? = null,
        override val timestamp: Instant,
        override val count: Int? = null
    ) : LedgerEvent() {
        override val type: LedgerEventType = LedgerEventType.TRIGGERED

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            SCHEDULE_ID to scheduleId,
            SHARED_ID to sharedId,
            TRIGGER_ID to triggerId,
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
     * execution count in `count`: it has no `triggerId` and its `sharedId` is
     * always null (recorded only under the schedule ID, so legacy history
     * never pollutes a group's pooled tally), and its timestamp reflects when
     * migration occurred, not when the original executions happened.
     */
    data class Execution(
        override val scheduleId: String,
        override val sharedId: String? = null,
        override val triggerId: String? = null,
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
            SHARED_ID to sharedId,
            TRIGGER_ID to triggerId,
            TIMESTAMP to timestamp.toEpochMilli(),
            COUNT to count,
            RESULT to result,
            CANCEL to cancel
        ).toJsonValue()
    }

    internal companion object {
        private const val TYPE = "type"
        private const val SCHEDULE_ID = "schedule_id"
        private const val SHARED_ID = "shared_id"
        private const val TRIGGER_ID = "trigger_id"
        private const val TIMESTAMP = "timestamp"
        private const val COUNT = "count"
        private const val RESULT = "result"
        private const val CANCEL = "cancel"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): LedgerEvent {
            val content = value.requireMap()
            return when (LedgerEventType.fromJson(content.require(TYPE))) {
                LedgerEventType.TRIGGERED -> Triggered(
                    scheduleId = content.requireField(SCHEDULE_ID),
                    sharedId = content.optionalField(SHARED_ID),
                    triggerId = content.optionalField(TRIGGER_ID),
                    timestamp = content.requireEpochMillis(TIMESTAMP),
                    count = content.optionalField(COUNT)
                )

                LedgerEventType.EXECUTION -> Execution(
                    scheduleId = content.requireField(SCHEDULE_ID),
                    sharedId = content.optionalField(SHARED_ID),
                    triggerId = content.optionalField(TRIGGER_ID),
                    timestamp = content.requireEpochMillis(TIMESTAMP),
                    count = content.optionalField(COUNT),
                    result = LedgerExecutionResult.fromJson(content.require(RESULT)),
                    cancel = content.optionalField(CANCEL)
                )
            }
        }
    }
}

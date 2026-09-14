/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import com.urbanairship.audience.TimeSpan
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import com.urbanairship.json.toJsonList

/**
 * Configures how a schedule's limit is evaluated against the ledger.
 *
 * The limit always counts the schedule's own `execution` events, of every
 * [LedgerExecutionResult] (never `triggered`); it never changes the cap
 * itself (the schedule's `limit`), only what counts toward it.
 *
 * The two variants are a hard split, not a flag with a doc caveat: with
 * [Self] (or no config at all), the tally is the schedule's own events, full
 * stop — nothing another schedule declares can affect it, and there's no
 * `source` field around to write a no-op reference to another schedule with.
 * Only [Shared] additionally counts events whose `schedule_id` or `shared_id`
 * matches this schedule's `ledger_config.shared_id` — not just events from
 * schedules that also declare that `shared_id` themselves, but any event
 * carrying it in either field, including a named schedule's own un-tagged
 * history (e.g. backfill). Only then does an [ExclusionRule.source] have
 * another schedule's events to actually subtract.
 */
internal sealed class LimitConfig : JsonSerializable {

    /**
     * The schedule's limit counts only its own events. [exclude] has no
     * `source` field: with no shared group in scope, "this schedule's own
     * events" is the only possible source.
     */
    data class Self(
        /**
         * Rules that remove recorded events from this schedule's own limit
         * tally. When absent, nothing is excluded.
         */
        val exclude: List<SelfExclusionRule>? = null
    ) : LimitConfig()

    /**
     * The schedule's limit also counts events matching its
     * `ledger_config.shared_id`, pooling its tally with them.
     */
    data class Shared(
        /**
         * Rules that remove recorded events from this schedule's limit tally
         * — its own events and pooled shared events alike. When absent,
         * nothing is excluded.
         */
        val exclude: ExclusionSet? = null
    ) : LimitConfig()

    internal companion object {
        private const val TYPE = "type"
        private const val EXCLUDE = "exclude"
        private const val OR = "or"
        private const val SELF = "self"
        private const val SHARED = "shared"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): LimitConfig {
            val content = value.requireMap()
            return when (content.optionalField<String>(TYPE)) {
                SHARED -> Shared(exclude = content.get(EXCLUDE)?.let(ExclusionSet::fromJson))
                // SELF, and anything unrecognized, default to the narrow,
                // safe variant — the same as omitting `limit_config` entirely.
                else -> Self(
                    exclude = content.get(EXCLUDE)?.requireMap()?.get(OR)?.requireList()
                        ?.map(SelfExclusionRule::fromJson)
                )
            }
        }
    }

    override fun toJsonValue(): JsonValue = when (this) {
        is Self -> jsonMapOf(
            TYPE to SELF,
            EXCLUDE to exclude?.let { jsonMapOf(OR to it.toJsonList()) }
        )

        is Shared -> jsonMapOf(TYPE to SHARED, EXCLUDE to exclude)
    }.toJsonValue()
}

/**
 * Excludes recorded events from a schedule's own limit tally
 * ([LimitConfig.Self]). No `source` field: with no shared group in scope,
 * "this schedule's own events" is the only possible source, so a rule is just
 * what to subtract.
 */
internal data class SelfExclusionRule(
    /**
     * Which of this schedule's own events to subtract. If null, every event
     * is subtracted.
     */
    val match: LedgerEventMatch? = null
) : JsonSerializable {

    internal companion object {
        private const val MATCH = "match"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): SelfExclusionRule = SelfExclusionRule(
            match = value.requireMap().get(MATCH)?.let(LedgerEventMatch::fromJson)
        )
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(MATCH to match).toJsonValue()
}

/**
 * A set of exclusion rules combined with a boolean operator. An event is
 * excluded when it matches ANY rule in [or] (logical OR). Room is left to add
 * other combinators (e.g. `and`) later.
 */
internal data class ExclusionSet(
    /** The rules OR'd together. An event is excluded when it matches any rule. */
    val or: List<ExclusionRule> = emptyList()
) : JsonSerializable {

    internal companion object {
        private const val OR = "or"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): ExclusionSet = ExclusionSet(
            or = value.requireMap().get(OR)?.requireList()?.map(ExclusionRule::fromJson)
                ?: emptyList()
        )
    }

    /** Whether [event] is removed from the tally: true when it matches ANY rule. */
    fun excludes(event: LedgerEvent, context: LedgerLimitContext): Boolean =
        or.any { it.matches(event, context) }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        OR to or.toJsonList()
    ).toJsonValue()
}

/**
 * Excludes recorded events from a schedule's limit tally. A rule subtracts
 * events that both come from [source] and satisfy [match] (every event from the
 * source when `match` is null).
 */
internal data class ExclusionRule(
    /** Which schedule's events this rule can subtract. */
    val source: LedgerSource,
    /**
     * Which of that source's events to subtract. If null, every event from the
     * source is subtracted.
     */
    val match: LedgerEventMatch? = null
) : JsonSerializable {

    internal companion object {
        private const val SOURCE = "source"
        private const val MATCH = "match"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): ExclusionRule {
            val content = value.requireMap()
            return ExclusionRule(
                source = LedgerSource.fromJson(content.require(SOURCE)),
                match = content.get(MATCH)?.let(LedgerEventMatch::fromJson)
            )
        }
    }

    /**
     * Whether this rule subtracts [event]: the event comes from [source] and (if
     * present) satisfies [match].
     */
    fun matches(event: LedgerEvent, context: LedgerLimitContext): Boolean {
        if (!source.matches(event, context)) {
            return false
        }
        return match?.matches(event, context) ?: true
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        SOURCE to source,
        MATCH to match
    ).toJsonValue()
}

/**
 * Selects which schedule recorded the events a rule applies to, relative to the
 * evaluating schedule. Candidates are already scoped to the schedule's ledger
 * IDs (its schedule ID and shared ledger ID); this picks among the schedule IDs
 * within that scope by matching each event's `schedule_id`.
 */
internal sealed class LedgerSource : JsonSerializable {

    /** Events recorded under the evaluating schedule's own schedule ID. */
    data object OwnSchedule : LedgerSource()

    /** Every schedule sharing the ledger except the evaluating one. */
    data object OtherSchedules : LedgerSource()

    /** A specific schedule, named absolutely by its schedule ID. */
    data class Schedule(val scheduleId: String) : LedgerSource()

    /** Any schedule sharing the ledger, including the evaluating schedule. */
    data object AnySchedule : LedgerSource()

    /**
     * A source type this SDK version does not recognize. Matches nothing, so a
     * rule carrying it subtracts no events (a safe no-op that errs toward
     * showing less).
     */
    data object Unknown : LedgerSource()

    /** Whether [event] was recorded by the schedule this source selects. */
    fun matches(event: LedgerEvent, context: LedgerLimitContext): Boolean = when (this) {
        is OwnSchedule -> event.scheduleId == context.scheduleId
        is OtherSchedules -> event.scheduleId != context.scheduleId
        is Schedule -> event.scheduleId == scheduleId
        is AnySchedule -> true
        is Unknown -> false
    }

    override fun toJsonValue(): JsonValue = when (this) {
        is OwnSchedule -> jsonMapOf(TYPE to OWN_SCHEDULE)
        is OtherSchedules -> jsonMapOf(TYPE to OTHER_SCHEDULES)
        is Schedule -> jsonMapOf(TYPE to SCHEDULE, SCHEDULE_ID to scheduleId)
        is AnySchedule -> jsonMapOf(TYPE to ANY)
        is Unknown -> jsonMapOf(TYPE to UNKNOWN)
    }.toJsonValue()

    internal companion object {
        private const val TYPE = "type"
        private const val SCHEDULE_ID = "schedule_id"

        private const val OWN_SCHEDULE = "own_schedule"
        private const val OTHER_SCHEDULES = "other_schedules"
        private const val SCHEDULE = "schedule"
        private const val ANY = "any"
        private const val UNKNOWN = "unknown"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): LedgerSource {
            val content = value.requireMap()
            return when (content.requireField<String>(TYPE)) {
                OWN_SCHEDULE -> OwnSchedule
                OTHER_SCHEDULES -> OtherSchedules
                SCHEDULE -> Schedule(content.requireField(SCHEDULE_ID))
                ANY -> AnySchedule
                // Forward compatible: an unrecognized source subtracts nothing.
                else -> Unknown
            }
        }
    }
}

/**
 * Matches recorded ledger events by their own properties, discriminated by
 * event `type` so each variant only exposes fields that exist on that event.
 *
 * Only `execution` events are counted, so [Execution.results] is the field that
 * matters for limit exclusions; a result value an older SDK does not recognize
 * simply fails to match, leaving the event counted — erring toward showing less.
 */
internal sealed class LedgerEventMatch : JsonSerializable {

    /** Matches `triggered` events. Has no result to match on. */
    data class Triggered(
        /**
         * Match only events recorded within this time span (against the event
         * timestamp). If null, no time bound is applied.
         */
        val timeBounds: TimeSpan? = null,
        /**
         * Match only events recorded under a matching shared group. If null,
         * shared group is not filtered.
         */
        val sharedGroup: SharedGroupMatch? = null,
        /**
         * Match only events whose `trigger_id` equals this. If null, all trigger
         * IDs match.
         */
        val triggerId: String? = null
    ) : LedgerEventMatch()

    /** Matches `execution` events, optionally narrowed by result and cancel. */
    data class Execution(
        /**
         * Match only events recorded within this time span (against the event
         * timestamp). If null, no time bound is applied.
         */
        val timeBounds: TimeSpan? = null,
        /**
         * Match only events recorded under a matching shared group. If null,
         * shared group is not filtered.
         */
        val sharedGroup: SharedGroupMatch? = null,
        /**
         * Execution results to match. If null, all results match. Result values
         * this SDK version does not recognize are dropped, so a rule that lists
         * only unrecognized results matches nothing (errs toward showing less).
         */
        val results: List<LedgerExecutionResult>? = null,
        /**
         * Match only events whose `cancel` flag equals this. If null, both
         * cancelled and non-cancelled executions match.
         */
        val cancel: Boolean? = null,
        /**
         * Match only events whose `trigger_id` equals this. If null, all trigger
         * IDs match.
         */
        val triggerId: String? = null
    ) : LedgerEventMatch()

    /**
     * A match type this SDK version does not recognize. Matches nothing, so a
     * rule carrying it subtracts no events (a safe no-op).
     */
    data object Unknown : LedgerEventMatch()

    /** Whether [event] satisfies this match. An absent field matches everything. */
    fun matches(event: LedgerEvent, context: LedgerLimitContext): Boolean = when {
        this is Triggered && event is LedgerEvent.Triggered ->
            (timeBounds?.isActive(event.timestamp) ?: true) &&
                    (sharedGroup?.matches(event.sharedId, context) ?: true) &&
                    (triggerId == null || triggerId == event.triggerId)

        this is Execution && event is LedgerEvent.Execution ->
            (timeBounds?.isActive(event.timestamp) ?: true) &&
                    (sharedGroup?.matches(event.sharedId, context) ?: true) &&
                    (results?.contains(event.result) ?: true) &&
                    (cancel == null || cancel == (event.cancel ?: false)) &&
                    (triggerId == null || triggerId == event.triggerId)

        // The match type does not apply to this event type, or is unknown.
        else -> false
    }

    override fun toJsonValue(): JsonValue = when (this) {
        is Triggered -> jsonMapOf(
            TYPE to TRIGGERED,
            TIME_BOUNDS to timeBounds,
            SHARED_GROUP to sharedGroup,
            TRIGGER_ID to triggerId
        )

        is Execution -> jsonMapOf(
            TYPE to EXECUTION,
            TIME_BOUNDS to timeBounds,
            SHARED_GROUP to sharedGroup,
            RESULTS to results?.toJsonList(),
            CANCEL to cancel,
            TRIGGER_ID to triggerId
        )

        is Unknown -> jsonMapOf(TYPE to UNKNOWN)
    }.toJsonValue()

    internal companion object {
        private const val TYPE = "type"
        private const val TIME_BOUNDS = "time_bounds"
        private const val SHARED_GROUP = "shared_group"
        private const val RESULTS = "results"
        private const val CANCEL = "cancel"
        private const val TRIGGER_ID = "trigger_id"

        private const val TRIGGERED = "triggered"
        private const val EXECUTION = "execution"
        private const val UNKNOWN = "unknown"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): LedgerEventMatch {
            val content = value.requireMap()
            val timeBounds = content.get(TIME_BOUNDS)?.let { TimeSpan.fromJson(it.requireMap()) }
            val sharedGroup = content.get(SHARED_GROUP)?.let(SharedGroupMatch::fromJson)
            val triggerId = content.optionalField<String>(TRIGGER_ID)

            return when (content.requireField<String>(TYPE)) {
                TRIGGERED -> Triggered(
                    timeBounds = timeBounds,
                    sharedGroup = sharedGroup,
                    triggerId = triggerId
                )

                EXECUTION -> Execution(
                    timeBounds = timeBounds,
                    sharedGroup = sharedGroup,
                    // Parse results leniently: drop any result value this SDK
                    // version does not recognize. A rule listing only unknown
                    // results ends up with an empty set, matching nothing.
                    // Recorded events keep their unrecognized result so they
                    // still count; a rule carrying one stays a no-op.
                    results = content.get(RESULTS)?.requireList()?.mapNotNull {
                        try {
                            LedgerExecutionResult.fromJson(it)
                                .takeUnless { result -> result is LedgerExecutionResult.Unknown }
                        } catch (e: JsonException) {
                            null
                        }
                    },
                    cancel = content.optionalField(CANCEL),
                    triggerId = triggerId
                )

                // Forward compatible: an unrecognized match subtracts nothing.
                else -> Unknown
            }
        }
    }
}

/**
 * Matches events by the shared group they were recorded under (their
 * `shared_id`), relative to the evaluating schedule's current shared group or by
 * an absolute ID.
 */
internal sealed class SharedGroupMatch : JsonSerializable {

    /**
     * Events recorded under the evaluating schedule's current shared group. If
     * the schedule has no shared group, matches events recorded with none.
     */
    data object Current : SharedGroupMatch()

    /**
     * The complement of [Current] — matches whatever that doesn't. When the
     * schedule has a current shared group, that's every event whose
     * `sharedId` differs, including events recorded with no shared group at
     * all. When the schedule has no current shared group, [Current] already
     * matches events recorded with none, so this matches none of them.
     */
    data object NotCurrent : SharedGroupMatch()

    /** Events recorded under a specific shared group, named absolutely. */
    data class Id(val sharedId: String) : SharedGroupMatch()

    /**
     * A shared-group match type this SDK version does not recognize. Matches
     * nothing (a safe no-op).
     */
    data object Unknown : SharedGroupMatch()

    /** Whether an event recorded under [sharedId] satisfies this match. */
    fun matches(sharedId: String?, context: LedgerLimitContext): Boolean = when (this) {
        is Current -> sharedId == context.currentSharedId
        is NotCurrent -> sharedId != context.currentSharedId
        is Id -> sharedId == this.sharedId
        is Unknown -> false
    }

    override fun toJsonValue(): JsonValue = when (this) {
        is Current -> jsonMapOf(TYPE to CURRENT)
        is NotCurrent -> jsonMapOf(TYPE to NOT_CURRENT)
        is Id -> jsonMapOf(TYPE to ID, SHARED_ID to sharedId)
        is Unknown -> jsonMapOf(TYPE to UNKNOWN)
    }.toJsonValue()

    internal companion object {
        private const val TYPE = "type"
        private const val SHARED_ID = "shared_id"

        private const val CURRENT = "current"
        private const val NOT_CURRENT = "not_current"
        private const val ID = "id"
        private const val UNKNOWN = "unknown"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): SharedGroupMatch {
            val content = value.requireMap()
            return when (content.requireField<String>(TYPE)) {
                CURRENT -> Current
                NOT_CURRENT -> NotCurrent
                ID -> Id(content.requireField(SHARED_ID))
                // Forward compatible: an unrecognized match subtracts nothing.
                else -> Unknown
            }
        }
    }
}

/**
 * The evaluating schedule's identity, used to resolve the relative [LedgerSource]
 * and [SharedGroupMatch] selectors against a recorded event.
 */
internal data class LedgerLimitContext(
    /** The evaluating schedule's own ID. */
    val scheduleId: String,
    /** The evaluating schedule's current shared ledger ID, if any. */
    val currentSharedId: String?
)

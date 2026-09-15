/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import com.urbanairship.UALog
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.ledgerSharedIds

/**
 * Evaluates whether a schedule has reached its execution limit by counting
 * eligible ledger events, replacing the legacy in-schedule execution counter.
 */
internal class LedgerLimitEvaluator(
    private val store: LedgerStoreInterface
) {

    /**
     * Whether the schedule is at or over its limit.
     *
     * Always counts the schedule's own `execution` events (every
     * [LedgerExecutionResult], never `triggered`). With a [LimitConfig.Shared]
     * config, additionally counts events matching any of the schedule's
     * `shared_ids` — matched against either the `schedule_id` or the groups of
     * another event, so a named schedule's pre-existing history is picked up
     * even if it predates any `shared_ids`. With [LimitConfig.Self] (or no
     * config at all), no other schedule's events can affect this one,
     * regardless of `shared_ids`. Subtracts any events removed by
     * `limit_config.exclude`, and compares the total against the schedule's
     * `limit` (null → 1, 0 → unlimited).
     */
    suspend fun isOverLimit(schedule: AutomationSchedule): Boolean {
        // null means 1, 0 means no limit.
        val limit = schedule.limit ?: 1U
        if (limit == 0U) {
            return false
        }

        val currentSharedIds = schedule.ledgerSharedIds
        val limitConfig = schedule.limitConfig
        // Only reach beyond the schedule's own events when it opted in; a
        // schedule's own payload alone must determine what can affect it.
        // Belonging to several groups widens what `Shared` reaches, but it is
        // still this flag alone that decides whether it reaches at all.
        val fetchSharedIds = when (limitConfig) {
            is LimitConfig.Shared -> currentSharedIds
            is LimitConfig.Self, null -> emptyList()
        }

        val events = try {
            store.events(scheduleId = schedule.identifier, sharedIds = fetchSharedIds)
        } catch (ex: Exception) {
            // A ledger read failure must never wedge execution. Err toward
            // showing the message rather than silently suppressing it.
            UALog.e(ex) { "Failed to read ledger for limit check ${schedule.identifier}" }
            return false
        }

        // A Self config's rules have no `source` to check — every fetched
        // event already is this schedule's own, since `fetchSharedIds` is empty
        // above — so OwnSchedule is the equivalent, and only, source.
        val exclude = when (limitConfig) {
            is LimitConfig.Self -> limitConfig.exclude?.let { rules ->
                ExclusionSet(rules.map { ExclusionRule(source = LedgerSource.OwnSchedule, match = it.match) })
            }

            is LimitConfig.Shared -> limitConfig.exclude
            null -> null
        }

        return isOverLimit(
            limit = limit,
            events = events,
            context = LedgerLimitContext(
                scheduleId = schedule.identifier,
                // The real current groups, not gated by the config variant:
                // exclusion rules filtering the schedule's own already-fetched
                // events (e.g. a `current` shared_group match) still need them.
                currentSharedIds = currentSharedIds.toSet()
            ),
            exclude = exclude
        )
    }

    internal companion object {

        /**
         * Pure limit evaluation over an already-fetched event set. [limit] is the
         * resolved cap (never null; the caller maps null → 1 and short-circuits 0).
         */
        fun isOverLimit(
            limit: UInt,
            events: List<LedgerEvent>,
            context: LedgerLimitContext,
            exclude: ExclusionSet?
        ): Boolean {
            var total = 0L
            val cap = limit.toLong()

            for (event in events) {
                // Only executions count toward the limit; triggered events never do.
                if (event !is LedgerEvent.Execution) {
                    continue
                }

                if (exclude?.excludes(event, context) == true) {
                    continue
                }

                total += event.effectiveCount
                if (total >= cap) {
                    return true
                }
            }

            return false
        }
    }
}

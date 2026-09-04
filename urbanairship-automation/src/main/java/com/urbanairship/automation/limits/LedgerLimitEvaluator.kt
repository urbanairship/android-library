/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import com.urbanairship.UALog
import com.urbanairship.automation.AutomationSchedule

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
     * Counts `execution` events of every [LedgerExecutionResult] (never
     * `triggered`) recorded under either of the schedule's ledger IDs
     * (`schedule_id` or its current `shared_id`), minus any events removed by
     * `limit_config.exclude`, and compares the total against the schedule's
     * `limit` (null → 1, 0 → unlimited).
     */
    suspend fun isOverLimit(schedule: AutomationSchedule): Boolean {
        // null means 1, 0 means no limit.
        val limit = schedule.limit ?: 1U
        if (limit == 0U) {
            return false
        }

        val sharedId = schedule.ledgerConfig?.sharedId

        val events = try {
            store.events(scheduleId = schedule.identifier, sharedId = sharedId)
        } catch (ex: Exception) {
            // A ledger read failure must never wedge execution. Err toward
            // showing the message rather than silently suppressing it.
            UALog.e(ex) { "Failed to read ledger for limit check ${schedule.identifier}" }
            return false
        }

        return isOverLimit(
            limit = limit,
            events = events,
            context = LedgerLimitContext(
                scheduleId = schedule.identifier,
                currentSharedId = sharedId
            ),
            exclude = schedule.limitConfig?.exclude
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

/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import java.time.Instant

/**
 * Spy [AutomationLedgerInterface] used across executor/preparer/engine tests to
 * assert exactly which ledger events a code path records, and which live IDs it
 * reconciles against.
 */
internal class TestAutomationLedger : AutomationLedgerInterface {

    sealed class Recorded {
        data class Triggered(
            val scheduleId: String,
            val sharedId: String?,
            val triggerId: String?
        ) : Recorded()

        data class Execution(
            val scheduleId: String,
            val sharedId: String?,
            val triggerId: String?,
            val result: LedgerExecutionResult,
            val cancel: Boolean
        ) : Recorded()

        /**
         * A guarded execution record. Kept distinct from [Execution] so a test
         * can assert the caller took the deduplicating path, and with what
         * cutoff. The guard itself is covered by `AutomationLedgerTest`.
         */
        data class ExecutionIfNoneSince(
            val scheduleId: String,
            val sharedId: String?,
            val triggerId: String?,
            val result: LedgerExecutionResult,
            val cancel: Boolean,
            val since: Instant
        ) : Recorded()

        /**
         * A reconciliation pass. Retention and compaction themselves are
         * covered by `LedgerStoreTest`; this only captures the live IDs the
         * caller derived.
         */
        data class Reconciled(
            val liveScheduleIds: Set<String>,
            val liveSharedIds: Set<String>
        ) : Recorded()
    }

    val recorded = mutableListOf<Recorded>()

    override suspend fun recordTriggered(
        scheduleId: String,
        sharedId: String?,
        triggerId: String?
    ) {
        recorded.add(Recorded.Triggered(scheduleId, sharedId, triggerId))
    }

    override suspend fun recordExecution(
        scheduleId: String,
        sharedId: String?,
        triggerId: String?,
        result: LedgerExecutionResult,
        cancel: Boolean
    ) {
        recorded.add(Recorded.Execution(scheduleId, sharedId, triggerId, result, cancel))
    }

    override suspend fun reconcile(liveScheduleIds: Set<String>, liveSharedIds: Set<String>) {
        recorded.add(Recorded.Reconciled(liveScheduleIds, liveSharedIds))
    }

    override suspend fun recordExecutionIfNoneSince(
        scheduleId: String,
        sharedId: String?,
        triggerId: String?,
        result: LedgerExecutionResult,
        cancel: Boolean,
        since: Instant
    ) {
        recorded.add(
            Recorded.ExecutionIfNoneSince(
                scheduleId, sharedId, triggerId, result, cancel, since
            )
        )
    }
}

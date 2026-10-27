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
            val sharedIds: List<String>,
            val triggerId: String?,
            val variantAudienceId: String? = null
        ) : Recorded()

        data class Execution(
            val scheduleId: String,
            val sharedIds: List<String>,
            val triggerId: String?,
            val variantAudienceId: String? = null,
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
            val sharedIds: List<String>,
            val triggerId: String?,
            val variantAudienceId: String? = null,
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
        sharedIds: List<String>,
        triggerId: String?,
        variantAudienceId: String?
    ) {
        recorded.add(Recorded.Triggered(scheduleId, sharedIds, triggerId, variantAudienceId))
    }

    override suspend fun recordExecution(
        scheduleId: String,
        sharedIds: List<String>,
        triggerId: String?,
        variantAudienceId: String?,
        result: LedgerExecutionResult,
        cancel: Boolean
    ) {
        recorded.add(Recorded.Execution(scheduleId, sharedIds, triggerId, variantAudienceId, result, cancel))
    }

    override suspend fun reconcile(liveScheduleIds: Set<String>, liveSharedIds: Set<String>) {
        recorded.add(Recorded.Reconciled(liveScheduleIds, liveSharedIds))
    }

    override suspend fun recordExecutionIfNoneSince(
        scheduleId: String,
        sharedIds: List<String>,
        triggerId: String?,
        variantAudienceId: String?,
        result: LedgerExecutionResult,
        cancel: Boolean,
        since: Instant
    ) {
        recorded.add(
            Recorded.ExecutionIfNoneSince(
                scheduleId, sharedIds, triggerId, variantAudienceId, result, cancel, since
            )
        )
    }
}

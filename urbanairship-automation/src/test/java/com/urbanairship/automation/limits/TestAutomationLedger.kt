/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

/**
 * Spy [AutomationLedgerInterface] used across executor/preparer/engine tests to
 * assert exactly which ledger events a code path records.
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
}

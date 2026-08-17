/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import com.urbanairship.UALog
import com.urbanairship.util.Clock

/**
 * Records ledger events from the live execution pipeline.
 *
 * This is the write-side counterpart to [LedgerStoreInterface]: it turns the
 * coarse execution outcomes observed by the engine, preparer, and executors
 * into [LedgerEvent]s and appends them to the store. Recording failures are
 * logged and swallowed — a ledger write must never break execution.
 */
internal interface AutomationLedgerInterface {

    /**
     * Records a `triggered` event: an execution-causing trigger reached its
     * goal.
     */
    suspend fun recordTriggered(
        scheduleId: String,
        sharedId: String?,
        triggerId: String?
    )

    /**
     * Records the single outcome `execution` event for an attempt.
     *
     * @param result How the execution resolved.
     * @param cancel True if this outcome also cancelled the schedule.
     */
    suspend fun recordExecution(
        scheduleId: String,
        sharedId: String?,
        triggerId: String?,
        result: LedgerExecutionResult,
        cancel: Boolean
    )
}

/** Ledger recorder backed by a [LedgerStoreInterface]. */
internal class AutomationLedger(
    private val store: LedgerStoreInterface,
    private val clock: Clock = Clock.DEFAULT_CLOCK
) : AutomationLedgerInterface {

    override suspend fun recordTriggered(
        scheduleId: String,
        sharedId: String?,
        triggerId: String?
    ) {
        record(
            LedgerEvent.Triggered(
                scheduleId = scheduleId,
                sharedId = sharedId,
                triggerId = triggerId,
                timestamp = clock.now()
            )
        )
    }

    override suspend fun recordExecution(
        scheduleId: String,
        sharedId: String?,
        triggerId: String?,
        result: LedgerExecutionResult,
        cancel: Boolean
    ) {
        record(
            LedgerEvent.Execution(
                scheduleId = scheduleId,
                sharedId = sharedId,
                triggerId = triggerId,
                timestamp = clock.now(),
                result = result,
                // Null keeps a non-cancelling outcome out of the JSON body.
                cancel = if (cancel) true else null
            )
        )
    }

    private suspend fun record(event: LedgerEvent) {
        try {
            store.recordEvents(listOf(event))
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to record ledger event $event" }
        }
    }
}

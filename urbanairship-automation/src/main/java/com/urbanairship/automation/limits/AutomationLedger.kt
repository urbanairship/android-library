/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import com.urbanairship.UALog
import com.urbanairship.util.Clock
import java.time.Instant

/**
 * Records ledger events from the live execution pipeline, and reconciles what
 * has been recorded against the schedules that are still live.
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

    /** Records the single outcome `execution` event for an attempt. */
    suspend fun recordExecution(
        scheduleId: String,
        sharedId: String?,
        triggerId: String?,
        result: LedgerExecutionResult,
        cancel: Boolean
    )

    /**
     * Records an `execution` event only when the schedule has recorded none of
     * its own at or after [since].
     *
     * For interruption recovery, which cannot tell whether the executor got to
     * record the outcome before the app went away. Passing the moment the
     * schedule started executing makes the recovery record a no-op when it did.
     */
    suspend fun recordExecutionIfNoneSince(
        scheduleId: String,
        sharedId: String?,
        triggerId: String?,
        result: LedgerExecutionResult,
        cancel: Boolean,
        since: Instant
    )

    /**
     * Reconciles the ledger against the live schedules: drops the events no
     * live schedule references any more, then compacts what remains.
     *
     * An event survives while any of the IDs it was recorded under is still
     * live, so a group's pooled history outlives individual variants.
     *
     * Unlike the record calls, failures are not swallowed here: reconciling is
     * maintenance that nothing in execution depends on, so the caller decides
     * what a failed pass means for it.
     *
     * @param liveScheduleIds Schedule IDs that still reference the ledger.
     * @param liveSharedIds Shared group IDs that still reference the ledger.
     */
    suspend fun reconcile(liveScheduleIds: Set<String>, liveSharedIds: Set<String>)
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
                cancel = cancel
            )
        )
    }

    override suspend fun recordExecutionIfNoneSince(
        scheduleId: String,
        sharedId: String?,
        triggerId: String?,
        result: LedgerExecutionResult,
        cancel: Boolean,
        since: Instant
    ) {
        if (hasRecordedExecutionSince(scheduleId, sharedId, since)) {
            UALog.v { "Execution already recorded for $scheduleId since $since, skipping" }
            return
        }

        recordExecution(scheduleId, sharedId, triggerId, result, cancel)
    }

    /**
     * Whether [scheduleId] recorded a real execution of its own at or after
     * [since].
     *
     * Backfill rows are ignored: they are synthesized from a pre-ledger count at
     * migration time, so they carry a timestamp newer than the interrupted
     * attempt they would otherwise mask, without standing for its outcome.
     *
     * A read failure answers false so the caller still records: missing an
     * execution lets a finished schedule run again, while a duplicate only
     * spends budget the schedule had already used.
     */
    private suspend fun hasRecordedExecutionSince(
        scheduleId: String,
        sharedId: String?,
        since: Instant
    ): Boolean = try {
        store.events(scheduleId, sharedId).any {
            it is LedgerEvent.Execution &&
                    it.scheduleId == scheduleId &&
                    it.result != LedgerExecutionResult.BACKFILL &&
                    !it.timestamp.isBefore(since)
        }
    } catch (ex: Exception) {
        UALog.e(ex) { "Failed to read ledger for $scheduleId, assuming nothing recorded" }
        false
    }

    override suspend fun reconcile(liveScheduleIds: Set<String>, liveSharedIds: Set<String>) {
        UALog.v { "Reconciling ledger against ${liveScheduleIds.size} live schedules" }

        // Retention first, so events orphaned by schedules that are gone are
        // dropped before the survivors are merged.
        store.retainEvents(liveScheduleIds, liveSharedIds)
        store.compact(clock.now())
    }

    private suspend fun record(event: LedgerEvent) {
        try {
            store.recordEvents(listOf(event))
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to record ledger event $event" }
        }
    }
}

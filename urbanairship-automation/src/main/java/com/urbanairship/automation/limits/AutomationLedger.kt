/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import com.urbanairship.UALog
import com.urbanairship.util.Clock
import kotlinx.coroutines.CancellationException
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
        val event = LedgerEvent.Execution(
            scheduleId = scheduleId,
            sharedId = sharedId,
            triggerId = triggerId,
            timestamp = clock.now(),
            result = result,
            cancel = cancel
        )

        try {
            // Checked and appended in one critical section, so a concurrent
            // recorder cannot slip between the two and let both write.
            val recorded = store.recordEventsUnless(scheduleId, sharedId, listOf(event)) {
                it.isRealExecutionBy(scheduleId, since)
            }

            if (!recorded) {
                UALog.v { "Execution already recorded for $scheduleId since $since, skipping" }
            }
        } catch (ex: CancellationException) {
            // An orderly shutdown, not a write failure. Recording again would
            // only throw at Room and log as if something went wrong.
            throw ex
        } catch (ex: Exception) {
            // Err toward recording: missing an execution lets a finished
            // schedule run again, while a duplicate only spends budget the
            // schedule had already used. The guarded append is atomic, so a
            // failure wrote nothing and this cannot double up.
            UALog.e(ex) { "Guarded ledger append failed for $scheduleId, recording unguarded" }
            record(event)
        }
    }

    /**
     * Whether this is a real execution [scheduleId] recorded itself at or after
     * [since].
     *
     * Backfill rows are ignored: they are synthesized from a pre-ledger count at
     * migration time, so they carry a timestamp newer than the interrupted
     * attempt they would otherwise mask, without standing for its outcome.
     */
    private fun LedgerEvent.isRealExecutionBy(scheduleId: String, since: Instant): Boolean =
        this is LedgerEvent.Execution &&
                this.scheduleId == scheduleId &&
                result != LedgerExecutionResult.BACKFILL &&
                !timestamp.isBefore(since)

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
        } catch (ex: CancellationException) {
            // Keeps propagating: a ledger write must never break execution, but
            // it must not swallow a cancellation either.
            throw ex
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to record ledger event $event" }
        }
    }
}

/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Serializes the executions that share a ledger group.
 *
 * Schedules that pool a budget through `ledger_config.shared_id` are limited by
 * counting `execution` events, but those events are only written once an
 * execution finishes — a message records nothing while it is still on screen.
 * Between the limit check and that write, a sibling in the same group reads a
 * stale tally and displays too.
 *
 * A reservation covers exactly that window: one in-flight execution per group,
 * so the next one only proceeds once the previous has recorded and the ledger
 * can answer honestly. Waiting rather than skipping matters — the holder may
 * still fail without spending the budget, and then the waiter should run.
 *
 * In-process only, and deliberately not persisted: it guards concurrent
 * execution on a single device, which is the whole of the gap. Anything that
 * outlives the process is already covered by the ledger.
 */
internal class LedgerGroupReservations {

    /** Guards [groups] itself, not the groups it holds. */
    private val guard = Mutex()
    private val groups = mutableMapOf<String, Group>()

    private class Group {
        /** Held for the duration of one execution. */
        val mutex = Mutex()

        /** Callers holding or waiting on [mutex], so an idle group can be dropped. */
        var users: Int = 0
    }

    /**
     * Runs [block] holding [sharedId], suspending until any current holder is
     * done.
     *
     * [Mutex] hands the lock to the longest-waiting caller, so a waiter cannot
     * be barged repeatedly — which matters when a reservation is held for a
     * whole display, since a barged waiter is one that never shows.
     *
     * Releasing is structural: a block that throws or is cancelled still hands
     * the group on rather than wedging it.
     */
    suspend fun <T> withGroup(sharedId: String, block: suspend () -> T): T {
        val group = guard.withLock {
            groups.getOrPut(sharedId) { Group() }.also { it.users += 1 }
        }

        try {
            return group.mutex.withLock { block() }
        } finally {
            // Non-cancellable so a cancelled execution still drops its claim. A
            // plain suspend here would abort in an already-cancelled coroutine
            // and leak the entry.
            withContext(NonCancellable) {
                guard.withLock {
                    group.users -= 1
                    if (group.users == 0) {
                        groups.remove(sharedId)
                    }
                }
            }
        }
    }

    /** Whether the group currently has an execution in flight. Test only. */
    @VisibleForTesting
    internal suspend fun isReserved(sharedId: String): Boolean =
        guard.withLock { groups[sharedId]?.mutex?.isLocked == true }

    /** How many callers hold or await the group. Test only. */
    @VisibleForTesting
    internal suspend fun users(sharedId: String): Int =
        guard.withLock { groups[sharedId]?.users ?: 0 }
}

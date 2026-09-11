/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
 * Closing that window takes two mechanisms, because not every execution can
 * hold a lock for its whole display:
 *
 * - [withGroup] is the reservation proper — one execution per group, for
 *   schedules that can hold it from the limit check through to the write.
 * - [enterInFlight] is for the ones that cannot, because a host shows them and
 *   may never do so. They hold the reservation only long enough to mark
 *   themselves, then release it and carry the mark through the display, which
 *   [inFlight] and [awaitInFlightClear] let siblings see and wait out.
 *
 * Either way a sibling waits rather than skipping, which matters because the
 * one ahead of it may still fail without spending the budget, and then the
 * waiter should run. Only [withGroup] waits fairly, though: [Mutex] hands off
 * to the longest-waiting caller, while callers woken from
 * [awaitInFlightClear] race for the group afresh and can be barged by a new
 * arrival.
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

        /** Callers holding or waiting on [mutex]. */
        var users: Int = 0

        /**
         * Executions that have started but whose event is not written yet, from
         * schedules that cannot hold [mutex] for their whole display. A
         * [StateFlow] rather than a plain count, so a caller can suspend until it
         * reaches zero without polling.
         */
        private val _inFlight = MutableStateFlow(0)
        val inFlight: StateFlow<Int> get() = _inFlight

        fun addInFlight(delta: Int) {
            _inFlight.value += delta
        }

        /** Nothing claims the group, so it can be dropped. */
        val isIdle: Boolean get() = users == 0 && inFlight.value == 0
    }

    private suspend fun claim(sharedId: String, change: (Group) -> Unit): Group =
        guard.withLock { groups.getOrPut(sharedId) { Group() }.also(change) }

    /**
     * Drops a claim, and the group with it once nothing claims it.
     *
     * Non-cancellable so a cancelled execution still lets go. A plain suspend
     * here would abort in an already-cancelled coroutine and leak the claim,
     * which for an in-flight mark would wedge the group's siblings for good.
     */
    private suspend fun dropClaim(sharedId: String, change: (Group) -> Unit) {
        withContext(NonCancellable) {
            guard.withLock {
                val group = groups[sharedId] ?: return@withLock
                change(group)
                if (group.isIdle) {
                    groups.remove(sharedId)
                }
            }
        }
    }

    /**
     * Runs [block] holding [sharedId], suspending until any current holder is
     * done.
     *
     * [Mutex] hands the lock to the longest-waiting caller, so a waiter here
     * cannot be barged repeatedly — which matters when a reservation is held
     * for a whole display, since a barged waiter is one that never shows.
     * Waiting on [awaitInFlightClear] carries no such guarantee.
     *
     * Releasing is structural: a block that throws or is cancelled still hands
     * the group on rather than wedging it.
     */
    suspend fun <T> withGroup(sharedId: String, block: suspend () -> T): T {
        val group = claim(sharedId) { it.users += 1 }
        try {
            return group.mutex.withLock { block() }
        } finally {
            dropClaim(sharedId) { it.users -= 1 }
        }
    }

    /**
     * Marks an execution in flight for [sharedId], for a schedule that cannot
     * hold the group across its own display.
     *
     * Deliberately not a scope function: the mark has to be taken while the
     * caller still holds the group, so a sibling taking it next can see this
     * execution, and released only once that execution ends — which is after
     * the group has been let go. Pair it with [exitInFlight] in a `finally`.
     */
    suspend fun enterInFlight(sharedId: String) {
        claim(sharedId) { it.addInFlight(1) }
    }

    /** Clears a mark taken by [enterInFlight]. */
    suspend fun exitInFlight(sharedId: String) {
        dropClaim(sharedId) { it.addInFlight(-1) }
    }

    /**
     * Executions in the group that have started but not recorded yet.
     *
     * A pending spend the ledger cannot answer for: counting it keeps a sibling
     * from reading a tally that is about to change.
     */
    suspend fun inFlight(sharedId: String): Int =
        guard.withLock { groups[sharedId]?.inFlight?.value ?: 0 }

    /**
     * Suspends until [sharedId] has no execution in flight, or returns at once
     * if it already has none.
     *
     * Never holds [sharedId]'s mutex or the group map's own lock while
     * suspended: an in-flight mark exists so a caller that cannot hold the
     * mutex for its own unbounded display still excludes siblings, and waiting
     * on it while holding anything would reintroduce exactly that unbounded
     * stall one level up.
     */
    suspend fun awaitInFlightClear(sharedId: String) {
        val inFlight = guard.withLock { groups[sharedId]?.inFlight } ?: return
        inFlight.first { it == 0 }
    }

    /**
     * Whether the group's reservation is currently held — distinct from
     * [inFlight], which an execution that cannot hold the reservation sets
     * instead. Test only.
     */
    @VisibleForTesting
    internal suspend fun isReserved(sharedId: String): Boolean =
        guard.withLock { groups[sharedId]?.mutex?.isLocked == true }

    /** How many callers hold or await the group. Test only. */
    @VisibleForTesting
    internal suspend fun users(sharedId: String): Int =
        guard.withLock { groups[sharedId]?.users ?: 0 }
}

/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import androidx.annotation.VisibleForTesting
import com.urbanairship.UALog
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
 * Schedules that pool a budget through `ledger_config.shared_ids` are limited
 * by counting `execution` events, but those events are only written once an
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
 * arrival. Belonging to several groups amplifies that unfairness rather than
 * adding a new kind: a waiter needs every one of its groups unmarked at the
 * same moment, and marks in groups that do not overlap each other are free to
 * interleave, so in principle a schedule pooling two busy groups can lose the
 * race indefinitely. Accepted rather than fixed — the alternative is holding
 * something while waiting, which is exactly the unbounded stall the in-flight
 * mark exists to avoid, and a mark only lasts one display.
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

        /**
         * Floored at zero, so the three places that read this count cannot
         * disagree. An unpaired clear would otherwise drive it negative, which
         * [inFlight]'s `> 0` reads as clear while [awaitInFlightClear]'s wait
         * for zero never sees — and the group would leak, since [isIdle] never
         * becomes true either.
         */
        fun addInFlight(delta: Int) {
            _inFlight.value = (_inFlight.value + delta).coerceAtLeast(0)
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
     * Runs [block] holding every group in [sharedIds], suspending until each is
     * free.
     *
     * A schedule can belong to several groups at once, and every one of them
     * has to be held for the execution — a sibling in any single shared group
     * would otherwise read a stale tally. The groups are taken one at a time
     * in sorted order, and that order is the whole of the deadlock argument:
     * two schedules overlapping on `[a, b]` both take `a` before `b`, so the
     * one that loses the race for `a` never sits on `b` while waiting for it.
     * Ordering through [LedgerSharedIds.canonical] rather than trusting
     * callers keeps the guarantee local — [AutomationSchedule.ledgerSharedIds]
     * is already canonical, so this is normally a no-op — and keeps this the
     * one place that defines what "canonical order" means, rather than a
     * second, independent sort that could quietly drift from the storage
     * layer's.
     *
     * [Mutex] hands each lock to its longest-waiting caller, so a waiter here
     * cannot be barged repeatedly — which matters when a reservation is held
     * for a whole display, since a barged waiter is one that never shows.
     * Waiting on [awaitInFlightClear] carries no such guarantee.
     *
     * Releasing is structural: a block that throws or is cancelled still hands
     * every held group on rather than wedging it, and [release] never replaces
     * what the block did with a failure of its own.
     */
    suspend fun <T> withGroup(sharedIds: List<String>, block: suspend () -> T): T {
        val ids = LedgerSharedIds.canonical(sharedIds)
        val claimed = mutableListOf<String>()
        val locked = mutableListOf<Group>()
        // Tracked the way `use` tracks it, so `release` can tell a cleanup
        // failure that should be attached to something already propagating
        // from one that would otherwise become the caller's only news.
        var primary: Throwable? = null
        try {
            for (sharedId in ids) {
                val group = claim(sharedId) { it.users += 1 }
                claimed.add(sharedId)
                group.mutex.lock()
                locked.add(group)
            }
            return block()
        } catch (e: Throwable) {
            primary = e
            throw e
        } finally {
            release(locked, claimed, primary)
        }
    }

    /**
     * Hands back everything [withGroup] took, in reverse acquisition order.
     *
     * Unlocked before the claim is dropped, mirroring what `mutex.withLock`'s
     * own internal finally would have done for a single group.
     *
     * Neither loop should ever actually throw — `unlock` only ever targets a
     * mutex the calling coroutine just locked itself, and [dropClaim] cannot
     * fail — but releasing is isolated per group anyway: one unexpected throw
     * must not abandon the rest of the release and leak every other group the
     * call is holding.
     *
     * Where such a throw goes depends on whether something is already on its
     * way out. With a [primary] in flight it is attached to that, the way `use`
     * attaches a failed `close`: the block's own failure is the one the caller
     * can act on, and a cancellation in particular must stay a cancellation
     * rather than surface as an ordinary error. With nothing propagating it is
     * logged instead of thrown, because by this point the block has run to
     * completion — throwing would report a finished execution as failed, which
     * is worse than a logged inconsistency in bookkeeping the caller cannot do
     * anything about either way.
     *
     * Non-cancellable as a whole for the reason [exitInFlight] is, and with
     * more at stake: this usually runs while a cancellation is already
     * unwinding, and a release that stopped part way would leave a [Mutex]
     * locked by a coroutine that is going away — a group no caller can ever
     * take again, rather than merely a mark no caller clears.
     */
    private suspend fun release(
        locked: List<Group>,
        claimed: List<String>,
        primary: Throwable?
    ) = withContext(NonCancellable) {
        val failures = mutableListOf<Throwable>()
        for (group in locked.asReversed()) {
            try {
                group.mutex.unlock()
            } catch (e: Throwable) {
                failures.add(e)
            }
        }
        for (sharedId in claimed.asReversed()) {
            try {
                dropClaim(sharedId) { it.users -= 1 }
            } catch (e: Throwable) {
                failures.add(e)
            }
        }
        for (failure in failures) {
            if (primary != null) {
                primary.addSuppressed(failure)
            } else {
                UALog.e(failure) { "Failed to release a ledger group." }
            }
        }
    }

    /**
     * Marks an execution in flight for every group in [sharedIds], for a
     * schedule that cannot hold the groups across its own display.
     *
     * Deliberately not a scope function: the mark has to be taken while the
     * caller still holds the groups, so a sibling taking one of them next can
     * see this execution, and released only once that execution ends — which
     * is after the groups have been let go. Pair it with [exitInFlight] in a
     * `finally`.
     *
     * Non-cancellable as a whole, the same way [dropClaim] is, and here the
     * wrapper is what provides it rather than reinforcing it: [claim] takes
     * [guard] through an ordinary cancellable `withLock`, so under contention
     * a schedule marking several groups in a loop could be cancelled part way
     * through. The caller's own `finally` guards the pairing, but only once
     * this call returns — a partially-applied mark that this call never
     * finishes has nothing pairing it yet, and those groups' siblings would
     * wedge in [awaitInFlightClear] for good.
     */
    suspend fun enterInFlight(sharedIds: List<String>) {
        withContext(NonCancellable) {
            for (sharedId in sharedIds.distinct()) {
                claim(sharedId) { it.addInFlight(1) }
            }
        }
    }

    /**
     * Clears the marks taken by [enterInFlight].
     *
     * Non-cancellable as a whole rather than only per group, which is belt and
     * braces and deliberately so. Each [dropClaim] is already non-cancellable
     * on its own, and as this is written the gap *between* two of them cannot
     * be interrupted either: [dropClaim]'s `withContext` keeps the caller's
     * interceptor, so it resumes undispatched and performs no cancellation
     * check on the way back into this loop. That is a detail of how
     * `withContext` chooses its coroutine, though — giving [dropClaim] a
     * dispatcher of its own would make each resume a dispatched one, which
     * does check — and the cost of not depending on it is a single wrapper.
     *
     * The consequence of being wrong is what justifies the paranoia: this runs
     * from a `finally` unwinding an already-cancelled coroutine, and a
     * schedule in several groups that cleared the first mark and then stopped
     * would leave the rest marked with no call left to ever clear them,
     * wedging every sibling in [awaitInFlightClear] for good.
     */
    suspend fun exitInFlight(sharedIds: List<String>) {
        withContext(NonCancellable) {
            for (sharedId in sharedIds.distinct()) {
                dropClaim(sharedId) { it.addInFlight(-1) }
            }
        }
    }

    /**
     * Executions across [sharedIds] that have started but not recorded yet.
     *
     * A pending spend the ledger cannot answer for: counting it keeps a sibling
     * from reading a tally that is about to change. A schedule in several
     * groups is affected by an in-flight execution in any one of them, so the
     * counts are summed — callers only ever compare the result against zero.
     */
    suspend fun inFlight(sharedIds: List<String>): Int =
        sharedIds.distinct().sumOf { sharedId -> guard.withLock { groups[sharedId]?.inFlight?.value ?: 0 } }

    /**
     * Suspends until every group in [sharedIds] has no execution in flight, or
     * returns at once if none currently have one.
     *
     * Never holds a group's mutex or the group map's own lock while suspended:
     * an in-flight mark exists so a caller that cannot hold the mutex for its
     * own unbounded display still excludes siblings, and waiting on it while
     * holding anything would reintroduce exactly that unbounded stall one
     * level up. Groups are awaited one at a time rather than concurrently,
     * which is only a latency detail — none of them block each other.
     */
    suspend fun awaitInFlightClear(sharedIds: List<String>) {
        for (sharedId in sharedIds.distinct()) {
            val inFlight = guard.withLock { groups[sharedId]?.inFlight } ?: continue
            inFlight.first { it == 0 }
        }
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

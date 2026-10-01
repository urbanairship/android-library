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
 * waiter should run.
 *
 * Two fairness caveats, both worse for a schedule in several groups. Callers
 * woken from [awaitInFlightClear] race for the group afresh and can be barged,
 * where [withGroup] waiters are handed the lock in arrival order; a schedule
 * needing N groups must win N of those races at once. And [withGroup] holds
 * the locks it has while waiting for the rest, so a long hold in one group can
 * stall schedules in another — see [withGroup] for why that is preferred to
 * the alternative. Belonging to several groups amplifies that unfairness rather than
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
     * Every group has to be held for the execution, or a sibling in any one of
     * them reads a stale tally. They are taken one at a time in canonical
     * sorted order, which is what makes this deadlock-free: two schedules
     * overlapping on `[a, b]` both take `a` first, so neither holds `b` while
     * waiting for `a`. Sorting here rather than trusting callers keeps that
     * guarantee local.
     *
     * Locks already taken are held while waiting for the next, so a long hold
     * in one group stalls schedules in another. Not separable from the
     * guarantee: something has to hold `a` to stop a stream of `a`-only
     * schedules starving a waiter on `[a, b]`, which would then fail by never
     * showing rather than by waiting.
     *
     * [release] hands every group back whether the block returns, throws or is
     * cancelled, and never replaces what the block did with a failure of its
     * own.
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
     * Each group is released in its own try/catch, so one unexpected throw
     * cannot abandon the rest and leak every other group still held. A failure
     * is attached to [primary] when the block is already failing, and logged
     * otherwise — throwing would report a finished execution as failed.
     *
     * Non-cancellable: this usually runs while a cancellation is unwinding,
     * and stopping part way would leave a [Mutex] locked by a coroutine that
     * is going away, which no caller could ever take again.
     *
     * @param primary The block's own failure, if it had one.
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
     * Non-cancellable, because [claim] takes [guard] through a cancellable
     * `withLock`: under contention a schedule marking several groups could
     * otherwise stop part way. The caller's `finally` only guards the pairing
     * once this returns, so a half-applied mark has nothing to clear it and
     * would wedge those groups' siblings in [awaitInFlightClear] for good.
     */
    suspend fun enterInFlight(sharedIds: List<String>) {
        withContext(NonCancellable) {
            markEachGroup(sharedIds) { sharedId -> claim(sharedId) { it.addInFlight(1) } }
        }
    }

    /**
     * Clears the marks taken by [enterInFlight].
     *
     * Non-cancellable as a whole, not just per [dropClaim]. This runs from a
     * `finally` unwinding an already-cancelled coroutine, and a schedule that
     * cleared the first mark and then stopped would leave the rest marked with
     * nothing left to clear them.
     */
    suspend fun exitInFlight(sharedIds: List<String>) {
        withContext(NonCancellable) {
            markEachGroup(sharedIds) { sharedId -> dropClaim(sharedId) { it.addInFlight(-1) } }
        }
    }

    /**
     * Applies [change] to every distinct ID, isolating each so one failure
     * cannot skip the rest.
     *
     * [claim] and [dropClaim] only mutate a map under [guard] and have no
     * realistic way to throw today. The isolation is here because the cost of
     * being wrong later is unbounded: a mark left behind by a skipped
     * iteration wedges that group's siblings for the life of the process, and
     * the caller cannot clear it — its `finally` is only armed once the whole
     * call returns.
     */
    private suspend fun markEachGroup(sharedIds: List<String>, change: suspend (String) -> Unit) {
        var failure: Throwable? = null
        for (sharedId in sharedIds.distinct()) {
            try {
                change(sharedId)
            } catch (e: Throwable) {
                failure?.addSuppressed(e) ?: run { failure = e }
            }
        }
        failure?.let { throw it }
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

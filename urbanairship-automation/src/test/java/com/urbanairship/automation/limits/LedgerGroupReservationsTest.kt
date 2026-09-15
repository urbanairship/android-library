/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class LedgerGroupReservationsTest {

    private val reservations = LedgerGroupReservations()

    @Test
    public fun testHolderBlocksSameGroup(): TestResult = runTest {
        val holderEntered = CompletableDeferred<Unit>()
        val releaseHolder = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()

        val holder = launch {
            reservations.withGroup(listOf("group-1")) {
                order.add("holder-in")
                holderEntered.complete(Unit)
                releaseHolder.await()
                order.add("holder-out")
            }
        }

        holderEntered.await()
        assertTrue(reservations.isReserved("group-1"))

        val waiter = launch {
            reservations.withGroup(listOf("group-1")) { order.add("waiter-in") }
        }

        // The waiter is parked, not running: the group is still the holder's.
        testScheduler.runCurrent()
        assertEquals(listOf("holder-in"), order)
        assertEquals(2, reservations.users("group-1"))

        releaseHolder.complete(Unit)
        holder.join()
        waiter.join()

        // The waiter only proceeds once the holder is completely done, so the
        // ledger can already answer for whatever the holder recorded.
        assertEquals(listOf("holder-in", "holder-out", "waiter-in"), order)
    }

    @Test
    public fun testDifferentGroupsDoNotBlockEachOther(): TestResult = runTest {
        val holderEntered = CompletableDeferred<Unit>()
        val releaseHolder = CompletableDeferred<Unit>()
        var otherRan = false

        val holder = launch {
            reservations.withGroup(listOf("group-1")) {
                holderEntered.complete(Unit)
                releaseHolder.await()
            }
        }

        holderEntered.await()

        val other = launch {
            reservations.withGroup(listOf("group-2")) { otherRan = true }
        }
        other.join()

        assertTrue(otherRan)

        releaseHolder.complete(Unit)
        holder.join()
    }

    @Test
    public fun testGroupIsReleasedWhenBlockThrows(): TestResult = runTest {
        val thrown = IllegalStateException("boom")
        val caught = runCatching {
            reservations.withGroup(listOf("group-1")) { throw thrown }
        }

        // The block's own failure is what reaches the caller. Releasing runs on
        // the way out and must never substitute a failure of its own: a caller
        // that cannot tell a cancelled execution from a failed one records the
        // wrong outcome for it.
        assertEquals(thrown, caught.exceptionOrNull())

        assertFalse(reservations.isReserved("group-1"))
        assertEquals(0, reservations.users("group-1"))

        // The group still works for the next caller rather than being wedged.
        var ran = false
        reservations.withGroup(listOf("group-1")) { ran = true }
        assertTrue(ran)
    }

    @Test
    public fun testGroupIsReleasedWhenHolderIsCancelled(): TestResult = runTest {
        val holderEntered = CompletableDeferred<Unit>()

        val holder = launch {
            reservations.withGroup(listOf("group-1")) {
                holderEntered.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
        }

        holderEntered.await()
        holder.cancelAndJoinQuietly()

        assertEquals(0, reservations.users("group-1"))

        var ran = false
        reservations.withGroup(listOf("group-1")) { ran = true }
        assertTrue(ran)
    }

    @Test
    public fun testAwaitInFlightClearReturnsImmediatelyWhenNothingIsInFlight(): TestResult = runTest {
        // Nothing ever marked "group-1" in flight, so there is nothing to wait
        // for - this must not suspend forever.
        reservations.awaitInFlightClear(listOf("group-1"))
    }

    @Test
    public fun testAwaitInFlightClearSuspendsUntilExitInFlight(): TestResult = runTest {
        reservations.enterInFlight(listOf("group-1"))

        var cleared = false
        val waiter = launch {
            reservations.awaitInFlightClear(listOf("group-1"))
            cleared = true
        }

        testScheduler.runCurrent()
        assertFalse(cleared)

        reservations.exitInFlight(listOf("group-1"))
        waiter.join()

        assertTrue(cleared)
    }

    @Test
    public fun testAwaitInFlightClearWaitsOutMultipleMarks(): TestResult = runTest {
        reservations.enterInFlight(listOf("group-1"))
        reservations.enterInFlight(listOf("group-1"))

        var cleared = false
        val waiter = launch {
            reservations.awaitInFlightClear(listOf("group-1"))
            cleared = true
        }

        reservations.exitInFlight(listOf("group-1"))
        testScheduler.runCurrent()
        // One of two marks cleared, so the group is still in flight.
        assertFalse(cleared)

        reservations.exitInFlight(listOf("group-1"))
        waiter.join()

        assertTrue(cleared)
    }

    /**
     * An in-flight mark never blocks [withGroup]: marking is how a caller that
     * cannot hold the group at all still excludes siblings, so it must not
     * additionally contend for the mutex.
     */
    @Test
    public fun testInFlightMarkDoesNotBlockWithGroup(): TestResult = runTest {
        reservations.enterInFlight(listOf("group-1"))

        var ran = false
        reservations.withGroup(listOf("group-1")) { ran = true }

        assertTrue(ran)
        reservations.exitInFlight(listOf("group-1"))
    }

    /**
     * `exitInFlight` is always called from a `finally` that may be unwinding
     * an already-cancelled coroutine (this is exactly how `AutomationEngine`
     * calls it). A schedule in several groups must have every one of them
     * cleared, not just the first, or the rest wedge every sibling in
     * [LedgerGroupReservations.awaitInFlightClear] for good.
     *
     * What this pins is the loop and the calling context, not the
     * `NonCancellable` wrapper around them: on a single-threaded test
     * dispatcher nothing contends `guard`, so the loop runs without ever
     * suspending and cancellation has no point at which to interrupt it. There
     * is no way to construct one from outside this class — `guard` is only
     * ever held across non-suspending critical sections — so the wrapper is
     * documented as the deliberate belt-and-braces it is rather than pretended
     * to be covered here.
     */
    @Test
    public fun testExitInFlightFromACancelledFinallyClearsEveryGroup(): TestResult = runTest {
        reservations.enterInFlight(listOf("a", "b"))

        val entered = CompletableDeferred<Unit>()
        val job = launch {
            try {
                entered.complete(Unit)
                awaitCancellation()
            } finally {
                reservations.exitInFlight(listOf("a", "b"))
            }
        }
        entered.await()
        job.cancelAndJoinQuietly()

        assertEquals(0, reservations.inFlight(listOf("a")))
        assertEquals(0, reservations.inFlight(listOf("b")))
    }

    /**
     * A clear with no matching mark must not drive the count below zero.
     * Negative is unrecoverable rather than merely odd: [inFlight] reads it as
     * clear and lets executions through, while a sibling already parked in
     * [LedgerGroupReservations.awaitInFlightClear] waits for exactly zero and
     * never wakes, and the group is never dropped because it never looks idle.
     */
    @Test
    public fun testUnpairedExitDoesNotDriveTheCountNegative(): TestResult = runTest {
        val holderEntered = CompletableDeferred<Unit>()
        val releaseHolder = CompletableDeferred<Unit>()

        // A holder keeps the group alive for the duration. Without one an
        // unpaired clear is a no-op — the group is already forgotten, so there
        // is no counter to take below zero and nothing to prove.
        val holder = launch {
            reservations.withGroup(listOf("a")) {
                holderEntered.complete(Unit)
                releaseHolder.await()
            }
        }
        holderEntered.await()

        reservations.exitInFlight(listOf("a"))

        assertEquals(0, reservations.inFlight(listOf("a")))

        // A later mark still has to register — clamping must not have left the
        // group holding a debt for the next caller to pay off.
        reservations.enterInFlight(listOf("a"))
        assertEquals(1, reservations.inFlight(listOf("a")))

        var cleared = false
        val waiter = launch {
            reservations.awaitInFlightClear(listOf("a"))
            cleared = true
        }
        testScheduler.runCurrent()
        assertFalse(cleared)

        reservations.exitInFlight(listOf("a"))
        waiter.join()
        assertTrue(cleared)

        releaseHolder.complete(Unit)
        holder.join()
    }

    // MARK: - Reading marks across several groups

    /**
     * A schedule is affected by an in-flight execution in any one of its
     * groups, so the sum has to cover all of them. Reading only the first
     * would let a schedule in `[group-a, shared]` execute against a tally that
     * cannot yet see a sibling mid-display under `shared` alone — both
     * messages on a budget of one, which is the whole failure the mark exists
     * to prevent.
     */
    @Test
    public fun testInFlightSeesAMarkInAnyGroupOfTheSet(): TestResult = runTest {
        reservations.enterInFlight(listOf("shared"))

        // Marked in neither the first nor the only group of the reading set.
        assertEquals(1, reservations.inFlight(listOf("group-a", "shared")))
        assertEquals(1, reservations.inFlight(listOf("shared", "group-a")))
        assertEquals(0, reservations.inFlight(listOf("group-a", "group-b")))

        reservations.exitInFlight(listOf("shared"))
        assertEquals(0, reservations.inFlight(listOf("group-a", "shared")))
    }

    @Test
    public fun testInFlightSumsAcrossGroups(): TestResult = runTest {
        reservations.enterInFlight(listOf("a"))
        reservations.enterInFlight(listOf("b"))

        assertEquals(2, reservations.inFlight(listOf("a", "b")))
        // A repeated ID is one group, not two.
        assertEquals(1, reservations.inFlight(listOf("a", "a")))
    }

    /**
     * The waiting half of the same rule: every group in the set has to clear,
     * and a waiter must not be released by the first one that does.
     */
    @Test
    public fun testAwaitInFlightClearWaitsForEveryGroupInTheSet(): TestResult = runTest {
        reservations.enterInFlight(listOf("a"))
        reservations.enterInFlight(listOf("b"))

        var cleared = false
        val waiter = launch {
            reservations.awaitInFlightClear(listOf("a", "b"))
            cleared = true
        }

        testScheduler.runCurrent()
        assertFalse(cleared)

        // The first group of the set clears; the second still holds the waiter.
        reservations.exitInFlight(listOf("a"))
        testScheduler.runCurrent()
        assertFalse(cleared)

        reservations.exitInFlight(listOf("b"))
        waiter.join()
        assertTrue(cleared)
    }


    @Test
    public fun testIdleGroupIsForgotten(): TestResult = runTest {
        reservations.withGroup(listOf("group-1")) { }

        // Nothing holds or awaits it, so the entry is dropped rather than
        // accumulating one per shared ID the app ever sees.
        assertEquals(0, reservations.users("group-1"))
        assertFalse(reservations.isReserved("group-1"))
    }

    // MARK: - Multiple groups per execution

    @Test
    public fun testReserveHoldsEveryGroupInTheSet(): TestResult = runTest {
        reservations.withGroup(listOf("a", "b")) {
            assertTrue(reservations.isReserved("a"))
            assertTrue(reservations.isReserved("b"))
        }

        assertFalse(reservations.isReserved("a"))
        assertFalse(reservations.isReserved("b"))
    }

    @Test
    public fun testReturnsTheBlockResult(): TestResult = runTest {
        assertEquals("result", reservations.withGroup(listOf("a", "b")) { "result" })
    }

    @Test
    public fun testOverlappingSetsSerialize(): TestResult = runTest {
        val holderEntered = CompletableDeferred<Unit>()
        val releaseHolder = CompletableDeferred<Unit>()
        var waiterAcquired = false

        // Holds `shared`; a schedule in `[group-b, shared]` overlaps on it.
        val holder = launch {
            reservations.withGroup(listOf("group-a", "shared")) {
                holderEntered.complete(Unit)
                releaseHolder.await()
            }
        }
        holderEntered.await()

        val waiter = launch {
            reservations.withGroup(listOf("group-b", "shared")) { waiterAcquired = true }
        }

        testScheduler.runCurrent()
        assertFalse(waiterAcquired)

        releaseHolder.complete(Unit)
        holder.join()
        waiter.join()

        assertTrue(waiterAcquired)
    }

    @Test
    public fun testDisjointSetsDoNotBlockEachOther(): TestResult = runTest {
        val holderEntered = CompletableDeferred<Unit>()
        val releaseHolder = CompletableDeferred<Unit>()
        var otherRan = false

        val holder = launch {
            reservations.withGroup(listOf("a", "b")) {
                holderEntered.complete(Unit)
                releaseHolder.await()
            }
        }
        holderEntered.await()

        // Would hang if any group were shared between the two sets.
        val other = launch {
            reservations.withGroup(listOf("c", "d")) { otherRan = true }
        }
        other.join()

        assertTrue(otherRan)

        releaseHolder.complete(Unit)
        holder.join()
    }

    /**
     * `withGroup` sorts its input but must also de-duplicate it: a repeated ID
     * within one call would otherwise try to take a group the same call
     * already holds — [Mutex] is not reentrant, so nesting it would hang
     * forever. Not reachable through [AutomationSchedule.ledgerSharedIds],
     * which is always canonical — this pins the actor's own behavior directly
     * so a future caller that isn't canonical doesn't wedge a group
     * permanently.
     */
    @Test
    public fun testDuplicateIdWithinOneCallDoesNotDeadlock(): TestResult = runTest {
        // Would hang forever before de-duplication.
        reservations.withGroup(listOf("a", "a")) {
            assertTrue(reservations.isReserved("a"))
        }

        assertFalse(reservations.isReserved("a"))
    }

    @Test
    public fun testOppositeOrderInputsDoNotDeadlock(): TestResult = runTest {
        val holderEntered = CompletableDeferred<Unit>()
        val releaseHolder = CompletableDeferred<Unit>()
        var acquired = false

        // Two schedules over the same pair, handed to `withGroup` in opposite
        // orders. Sorted acquisition is what keeps one from holding `b` while
        // waiting for `a` as the other does the reverse — without it these two
        // can wedge against each other forever.
        val holder = launch {
            reservations.withGroup(listOf("a", "b")) {
                holderEntered.complete(Unit)
                releaseHolder.await()
            }
        }
        holderEntered.await()

        val waiter = launch {
            reservations.withGroup(listOf("b", "a")) { acquired = true }
        }

        // Blocked on the lower of the two: queued behind the holder on "a",
        // and never having so much as attempted "b" yet. If the waiter's
        // reversed input order were taken at face value, it would instead
        // queue on "b" here — which is already held by the same holder, so
        // this is the only observable difference between the two orderings.
        testScheduler.runCurrent()
        assertFalse(acquired)
        assertEquals(2, reservations.users("a"))
        assertEquals(1, reservations.users("b"))

        releaseHolder.complete(Unit)
        holder.join()
        waiter.join()

        assertTrue(acquired)
    }

    private suspend fun kotlinx.coroutines.Job.cancelAndJoinQuietly() {
        cancel()
        join()
    }
}

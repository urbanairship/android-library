/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CompletableDeferred
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
            reservations.withGroup("group-1") {
                order.add("holder-in")
                holderEntered.complete(Unit)
                releaseHolder.await()
                order.add("holder-out")
            }
        }

        holderEntered.await()
        assertTrue(reservations.isReserved("group-1"))

        val waiter = launch {
            reservations.withGroup("group-1") { order.add("waiter-in") }
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
            reservations.withGroup("group-1") {
                holderEntered.complete(Unit)
                releaseHolder.await()
            }
        }

        holderEntered.await()

        val other = launch {
            reservations.withGroup("group-2") { otherRan = true }
        }
        other.join()

        assertTrue(otherRan)

        releaseHolder.complete(Unit)
        holder.join()
    }

    @Test
    public fun testGroupIsReleasedWhenBlockThrows(): TestResult = runTest {
        runCatching {
            reservations.withGroup("group-1") { throw IllegalStateException("boom") }
        }

        assertFalse(reservations.isReserved("group-1"))
        assertEquals(0, reservations.users("group-1"))

        // The group still works for the next caller rather than being wedged.
        var ran = false
        reservations.withGroup("group-1") { ran = true }
        assertTrue(ran)
    }

    @Test
    public fun testGroupIsReleasedWhenHolderIsCancelled(): TestResult = runTest {
        val holderEntered = CompletableDeferred<Unit>()

        val holder = launch {
            reservations.withGroup("group-1") {
                holderEntered.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
        }

        holderEntered.await()
        holder.cancelAndJoinQuietly()

        assertEquals(0, reservations.users("group-1"))

        var ran = false
        reservations.withGroup("group-1") { ran = true }
        assertTrue(ran)
    }

    @Test
    public fun testIdleGroupIsForgotten(): TestResult = runTest {
        reservations.withGroup("group-1") { }

        // Nothing holds or awaits it, so the entry is dropped rather than
        // accumulating one per shared ID the app ever sees.
        assertEquals(0, reservations.users("group-1"))
        assertFalse(reservations.isReserved("group-1"))
    }

    private suspend fun kotlinx.coroutines.Job.cancelAndJoinQuietly() {
        cancel()
        join()
    }
}

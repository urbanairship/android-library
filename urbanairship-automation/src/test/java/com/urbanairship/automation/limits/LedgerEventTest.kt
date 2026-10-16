/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import java.time.Instant
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers how a [LedgerEvent] decodes its recording scopes, and the
 * canonicalization every one of them passes through.
 *
 * Decoding is where an upgrade is won or lost: the storage migration promotes
 * the old `sharedId` SQL column into the join table but leaves each row's
 * persisted `body` exactly as the previous build wrote it, so every event on a
 * device that has ever run a pre-multi-group SDK arrives here in the older
 * shape and has to come out with its group intact.
 */
@RunWith(AndroidJUnit4::class)
public class LedgerEventTest {

    private fun decode(json: String): LedgerEvent =
        LedgerEvent.fromJson(JsonValue.parseString(json))

    // region Legacy single-ID key

    /**
     * The exact body a pre-multi-group SDK persisted for a pooled execution.
     * Losing the group here is silent and unrecoverable: the event stays in the
     * database, it just stops belonging to the pool, so every sibling's tally
     * shrinks and messages that were correctly suppressed display again.
     */
    @Test
    public fun testLegacySharedIdKeyStillPools() {
        val event = decode(
            """
            {
              "type": "execution",
              "schedule_id": "schedule-1",
              "shared_id": "group-1",
              "timestamp": 1000,
              "result": "succeeded"
            }
            """
        )

        assertEquals(listOf("group-1"), event.sharedIds)
    }

    @Test
    public fun testLegacySharedIdKeyOnATriggeredEvent() {
        val event = decode(
            """
            {
              "type": "triggered",
              "schedule_id": "schedule-1",
              "shared_id": "group-1",
              "timestamp": 1000
            }
            """
        )

        assertEquals(listOf("group-1"), event.sharedIds)
    }

    /** Both keys present: the current shape wins rather than merging. */
    @Test
    public fun testCurrentKeyWinsOverLegacyKey() {
        val event = decode(
            """
            {
              "type": "execution",
              "schedule_id": "schedule-1",
              "shared_ids": ["group-1"],
              "shared_id": "legacy-group",
              "timestamp": 1000,
              "result": "succeeded"
            }
            """
        )

        assertEquals(listOf("group-1"), event.sharedIds)
    }

    /**
     * An explicit empty array means the recording schedule had no groups, so
     * it must not fall through to the legacy key and resurrect one.
     */
    @Test
    public fun testExplicitEmptyArrayDoesNotFallBackToLegacyKey() {
        val event = decode(
            """
            {
              "type": "execution",
              "schedule_id": "schedule-1",
              "shared_ids": [],
              "shared_id": "legacy-group",
              "timestamp": 1000,
              "result": "succeeded"
            }
            """
        )

        assertEquals(emptyList<String>(), event.sharedIds)
    }

    @Test
    public fun testNeitherKeyIsNull() {
        val event = decode(
            """
            {
              "type": "execution",
              "schedule_id": "schedule-1",
              "timestamp": 1000,
              "result": "succeeded"
            }
            """
        )

        assertNull(event.sharedIds)
    }

    /**
     * Re-encoding an event decoded from the legacy key emits the current
     * shape, so the old spelling never round-trips back out and the fallback
     * is needed exactly once per row.
     */
    @Test
    public fun testLegacyKeyReencodesAsArray() {
        val event = decode(
            """
            {
              "type": "execution",
              "schedule_id": "schedule-1",
              "shared_id": "group-1",
              "timestamp": 1000,
              "result": "succeeded"
            }
            """
        )

        val reencoded = event.toJsonValue().toString()
        assertTrue(reencoded.contains("\"shared_ids\""))
        assertTrue(!reencoded.contains("\"shared_id\":"))

        assertEquals(listOf("group-1"), LedgerEvent.fromJson(event.toJsonValue()).sharedIds)
    }

    // endregion
    // region Canonicalization

    @Test
    public fun testCanonicalSortsAndDeduplicates() {
        assertEquals(
            listOf("group-1", "group-2"),
            LedgerSharedIds.canonical(listOf("group-2", "group-1", "group-2"))
        )
    }

    /**
     * An empty ID is dropped rather than carried. Kept as a group it would be
     * stamped onto every event the schedule records and, worse, become a
     * [LedgerGroupReservations] lock key — one every other schedule that ever
     * received a blank entry would queue on, serializing unrelated campaigns
     * and pooling their budgets under a group none of them belongs to.
     */
    @Test
    public fun testCanonicalDropsEmptyIds() {
        assertEquals(listOf("promo"), LedgerSharedIds.canonical(listOf("", "promo")))
        assertEquals(emptyList<String>(), LedgerSharedIds.canonical(listOf("", "")))
    }

    @Test
    public fun testCanonicalOfNullOrEmptyIsEmpty() {
        assertEquals(emptyList<String>(), LedgerSharedIds.canonical(null))
        assertEquals(emptyList<String>(), LedgerSharedIds.canonical(emptyList()))
    }

    @Test
    public fun testCanonicalOrNullCollapsesEmptyToNull() {
        assertNull(LedgerSharedIds.canonicalOrNull(emptyList()))
        assertNull(LedgerSharedIds.canonicalOrNull(listOf("")))
        assertEquals(listOf("promo"), LedgerSharedIds.canonicalOrNull(listOf("", "promo")))
    }

    /**
     * Two events recorded under the same groups in different orders have to
     * compare equal, which is what lets [LedgerCompactor] merge them.
     */
    @Test
    public fun testEventsWithTheSameGroupsInDifferentOrdersAreEqual() {
        val timestamp = Instant.ofEpochMilli(1000)
        fun event(sharedIds: List<String>) = LedgerEvent.Execution(
            scheduleId = "schedule-1",
            sharedIds = LedgerSharedIds.canonicalOrNull(sharedIds),
            timestamp = timestamp,
            result = LedgerExecutionResult.SUCCEEDED
        )

        assertEquals(event(listOf("a", "b")), event(listOf("b", "a")))
    }

    // endregion
}

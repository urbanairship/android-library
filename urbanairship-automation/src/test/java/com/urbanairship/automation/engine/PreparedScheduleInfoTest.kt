package com.urbanairship.automation.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.audience.VariantAudience
import com.urbanairship.json.jsonMapOf
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class PreparedScheduleInfoTest {

    @Test
    public fun testUnrecognizedVariantOutcomeKeepsTheSchedule() {
        // AutomationStore discards the entire stored schedule when this throws, so an
        // outcome stamped by a newer SDK has to parse rather than reject.
        val info = PreparedScheduleInfo.fromJson(
            jsonMapOf(
                "schedule_id" to "test-schedule",
                "trigger_session_id" to "some-session",
                "variant_audience_result" to jsonMapOf("outcome" to "some_future_arm")
            ).toJsonValue()
        )

        assertEquals("test-schedule", info.scheduleId)
        assertEquals("some-session", info.triggerSessionId)
        assertEquals(
            VariantAudienceResult(VariantAudience.Outcome.Unknown("some_future_arm")),
            info.variantAudienceResult
        )
    }

    /**
     * `ledgerSharedIds` replaced the single-ID `ledger_shared_id` key. A
     * prepared schedule is persisted as encoded JSON and outlives an app
     * upgrade, so one prepared by the previous build and executed by this one
     * must still record its outcome under the right shared group.
     */
    @Test
    public fun testParseLegacySingleSharedId() {
        val info = PreparedScheduleInfo.fromJson(
            jsonMapOf(
                "schedule_id" to "test-schedule",
                "trigger_session_id" to "some-session",
                "ledger_shared_id" to "group-1"
            ).toJsonValue()
        )

        assertEquals(listOf("group-1"), info.ledgerSharedIds)
    }

    /** Both keys present: the current shape wins rather than merging. */
    @Test
    public fun testCurrentLedgerSharedIdsWinsOverLegacyKey() {
        val info = PreparedScheduleInfo.fromJson(
            jsonMapOf(
                "schedule_id" to "test-schedule",
                "trigger_session_id" to "some-session",
                "ledger_shared_ids" to listOf("group-1"),
                "ledger_shared_id" to "legacy-group"
            ).toJsonValue()
        )

        assertEquals(listOf("group-1"), info.ledgerSharedIds)
    }

    /** Canonical form: de-duplicated and sorted, whatever order they arrive in. */
    @Test
    public fun testLedgerSharedIdsCanonicalizes() {
        val info = PreparedScheduleInfo.fromJson(
            jsonMapOf(
                "schedule_id" to "test-schedule",
                "trigger_session_id" to "some-session",
                "ledger_shared_ids" to listOf("group-2", "group-1", "group-1")
            ).toJsonValue()
        )

        assertEquals(listOf("group-1", "group-2"), info.ledgerSharedIds)
    }

    /**
     * An explicitly empty `ledger_shared_ids` means "no pooling" and must not
     * fall through to the legacy key, the same way [PreparedScheduleInfo]'s
     * sibling `LedgerConfig` treats the equivalent case.
     */
    @Test
    public fun testEmptyLedgerSharedIdsDoesNotFallBackToLegacyKey() {
        val info = PreparedScheduleInfo.fromJson(
            jsonMapOf(
                "schedule_id" to "test-schedule",
                "trigger_session_id" to "some-session",
                "ledger_shared_ids" to emptyList<String>(),
                "ledger_shared_id" to "legacy-group"
            ).toJsonValue()
        )

        assertEquals(emptyList<String>(), info.ledgerSharedIds)
    }

    /**
     * Re-encoding a schedule info decoded from the legacy key emits the
     * current shape, so the old spelling never round-trips back out.
     */
    @Test
    public fun testLegacyLedgerSharedIdReencodesAsArray() {
        val info = PreparedScheduleInfo.fromJson(
            jsonMapOf(
                "schedule_id" to "test-schedule",
                "trigger_session_id" to "some-session",
                "ledger_shared_id" to "group-1"
            ).toJsonValue()
        )

        val reencoded = info.toJsonValue().toString()
        assertTrue(reencoded.contains("ledger_shared_ids"))
        assertFalse(reencoded.contains("\"ledger_shared_id\":"))

        val roundTripped = PreparedScheduleInfo.fromJson(info.toJsonValue())
        assertEquals(listOf("group-1"), roundTripped.ledgerSharedIds)
    }

    /**
     * A schedule with no ledger config omits the key entirely rather than
     * writing `[]`, matching `LedgerConfig` and `LedgerEvent`. Most prepared
     * schedules are unpooled, so an empty array would put a dead key in nearly
     * every persisted row — and make the same schedule serialize differently
     * before and after this change.
     */
    @Test
    public fun testUnpooledScheduleOmitsTheSharedIdsKey() {
        val info = PreparedScheduleInfo(
            scheduleId = "test-schedule",
            triggerSessionId = "some-session"
        )

        assertFalse(info.toJsonValue().toString().contains("ledger_shared_ids"))

        // Absent still decodes back to empty, so the omission round-trips.
        assertEquals(
            emptyList<String>(),
            PreparedScheduleInfo.fromJson(info.toJsonValue()).ledgerSharedIds
        )
    }
}

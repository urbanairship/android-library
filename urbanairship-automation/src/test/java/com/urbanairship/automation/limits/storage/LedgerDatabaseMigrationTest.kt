/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits.storage

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.urbanairship.automation.limits.LedgerEvent
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers [LedgerDatabase.MIGRATION_1_2] against a real version-1 database,
 * rather than the fresh-at-current-version database every other test in this
 * package uses. [MigrationTestHelper] validates the post-migration schema
 * against what Room's `@Entity`/`@ForeignKey`/`@Index` annotations expect, so
 * a hand-written migration SQL string that drifts from the entities fails
 * here instead of only on a real device upgrading in production.
 */
@RunWith(AndroidJUnit4::class)
public class LedgerDatabaseMigrationTest {

    private companion object {
        private const val TEST_DB = "ledger-migration-test.db"
    }

    @get:Rule
    @JvmField
    public val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LedgerDatabase::class.java
    )

    @Test
    public fun testMigrationPreservesEventsAndPromotesSharedGroups() {
        var db = helper.createDatabase(TEST_DB, 1)

        // A pooled event (has a legacy sharedId) and an unpooled one, seeded
        // through the v1 schema directly.
        insertV1Event(db, id = 1, scheduleId = "schedule-1", sharedId = "group-1", timestamp = 1_000)
        insertV1Event(db, id = 2, scheduleId = "schedule-2", sharedId = null, timestamp = 2_000)
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, LedgerDatabase.MIGRATION_1_2)

        // Per-schedule history for both events survived untouched.
        val events = db.query("SELECT id, scheduleId, timestamp, body FROM ledger_events ORDER BY id")
        assertEquals(2, events.count)
        events.moveToFirst()
        assertEquals("schedule-1", events.getString(events.getColumnIndexOrThrow("scheduleId")))
        assertEquals(1_000L, events.getLong(events.getColumnIndexOrThrow("timestamp")))
        events.moveToNext()
        assertEquals("schedule-2", events.getString(events.getColumnIndexOrThrow("scheduleId")))
        events.close()

        // The pooled event's group association was promoted into the new
        // table; the unpooled event contributed no row.
        val sharedIds = db.query("SELECT eventId, value FROM ledger_shared_ids")
        assertEquals(1, sharedIds.count)
        sharedIds.moveToFirst()
        assertEquals(1L, sharedIds.getLong(sharedIds.getColumnIndexOrThrow("eventId")))
        assertEquals("group-1", sharedIds.getString(sharedIds.getColumnIndexOrThrow("value")))
        sharedIds.close()
    }

    /**
     * The rebuild copies `id` across explicitly, and the promoted
     * `ledger_shared_ids` rows point at those ids. Seeded sparsely on purpose:
     * ids 1 and 2 are exactly what AUTOINCREMENT would hand out anyway, so only
     * gaps can tell a copy from a renumbering — and gaps are what a real device
     * has, since compaction and retention both delete rows.
     */
    @Test
    public fun testMigrationPreservesSparseEventIds() {
        var db = helper.createDatabase(TEST_DB, 1)
        insertV1Event(db, id = 57, scheduleId = "schedule-1", sharedId = "group-1", timestamp = 1_000)
        insertV1Event(db, id = 104, scheduleId = "schedule-2", sharedId = "group-2", timestamp = 2_000)
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, LedgerDatabase.MIGRATION_1_2)

        val events = db.query("SELECT id FROM ledger_events ORDER BY id")
        val ids = mutableListOf<Long>()
        while (events.moveToNext()) {
            ids.add(events.getLong(0))
        }
        events.close()
        assertEquals(listOf(57L, 104L), ids)

        // And the promoted associations still point at those same rows. A
        // renumbering would leave every one of them dangling, silently
        // unpooling the device's whole ledger on upgrade.
        val pairs = db.query("SELECT eventId, value FROM ledger_shared_ids ORDER BY eventId")
        val associations = mutableListOf<Pair<Long, String>>()
        while (pairs.moveToNext()) {
            associations.add(pairs.getLong(0) to pairs.getString(1))
        }
        pairs.close()
        assertEquals(listOf(57L to "group-1", 104L to "group-2"), associations)
    }

    /**
     * The other half of the upgrade, and the half the migration itself cannot
     * cover: the rows survive, but each one's `body` is still the JSON the
     * previous SDK wrote, so the event only keeps its group if decoding still
     * understands the single-ID key. The two halves have to agree — a promoted
     * join-table row whose body decodes to no groups is worse than useless,
     * because the first compaction pass would then rewrite the row from the
     * decoded event and delete the association the migration had just created.
     */
    @Test
    public fun testMigratedBodyStillDecodesWithItsSharedGroup() {
        var db = helper.createDatabase(TEST_DB, 1)
        insertV1Event(db, id = 1, scheduleId = "schedule-1", sharedId = "group-1", timestamp = 1_000)
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, LedgerDatabase.MIGRATION_1_2)

        val rows = db.query("SELECT body FROM ledger_events WHERE id = 1")
        rows.moveToFirst()
        val body = rows.getString(rows.getColumnIndexOrThrow("body"))
        rows.close()

        val event = LedgerEvent.fromJson(JsonValue.parseString(body))
        assertEquals(listOf("group-1"), event.sharedIds)
    }

    /**
     * The migration creates `ledger_shared_ids` (with its foreign key to the
     * rebuilt `ledger_events`) only after that table has taken its final
     * name. This pins that the cascade is actually wired up afterward, not
     * just that both tables happen to exist.
     */
    @Test
    public fun testForeignKeyCascadeWorksImmediatelyAfterMigration() {
        var db = helper.createDatabase(TEST_DB, 1)
        insertV1Event(db, id = 1, scheduleId = "schedule-1", sharedId = "group-1", timestamp = 1_000)
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, LedgerDatabase.MIGRATION_1_2)

        // MigrationTestHelper hands back the raw connection the migration ran
        // on, which Room leaves with foreign-key enforcement off for the
        // migration itself; the real app only ever sees this table through a
        // normal RoomDatabase, which turns it on. Turn it on explicitly here
        // to exercise the same cascade a real reader would get.
        db.execSQL("PRAGMA foreign_keys=ON")
        db.execSQL("DELETE FROM ledger_events WHERE id = 1")

        val remaining = db.query("SELECT COUNT(*) FROM ledger_shared_ids")
        remaining.moveToFirst()
        val count = remaining.getInt(0)
        remaining.close()
        assertEquals(0, count)
    }

    @Test
    public fun testMigrationDropsTheLegacySharedIdColumn() {
        var db = helper.createDatabase(TEST_DB, 1)
        // Seeded, not an empty table: the rebuild (copy, drop, rename) has to
        // prove itself against a table that actually holds a row using the
        // column being dropped, not just an empty shell of one.
        insertV1Event(db, id = 1, scheduleId = "schedule-1", sharedId = "group-1", timestamp = 1_000)
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, LedgerDatabase.MIGRATION_1_2)

        val columns = mutableListOf<String>()
        val info = db.query("PRAGMA table_info(ledger_events)")
        while (info.moveToNext()) {
            columns.add(info.getString(info.getColumnIndexOrThrow("name")))
        }
        info.close()

        assertFalse(columns.contains("sharedId"))
        assertTrue(columns.containsAll(listOf("id", "scheduleId", "timestamp", "body")))
    }

    private fun insertV1Event(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        id: Int,
        scheduleId: String,
        sharedId: String?,
        timestamp: Long
    ) {
        // The body carries the group under the single-ID key too, exactly as
        // the SDK that wrote these rows encoded it. The migration only promotes
        // the `sharedId` column, never rewriting the body, so a fixture that
        // left the key out would be testing a row shape no device ever has.
        val legacyKey = if (sharedId != null) """"shared_id":"$sharedId",""" else ""
        val values = ContentValues().apply {
            put("id", id)
            put("scheduleId", scheduleId)
            if (sharedId != null) put("sharedId", sharedId) else putNull("sharedId")
            put("timestamp", timestamp)
            put(
                "body",
                """{"type":"execution","schedule_id":"$scheduleId",$legacyKey"timestamp":$timestamp,"result":"succeeded"}"""
            )
        }
        db.insert("ledger_events", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, values)
    }
}

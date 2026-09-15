/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits.storage

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.room.Database
import androidx.room.Room
import androidx.room.Room.databaseBuilder
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.json.JsonTypeConverters
import java.io.File

/**
 * @hide
 */
@Database(entities = [LedgerEventEntity::class, LedgerSharedIdEntity::class], version = 2)
@TypeConverters(JsonTypeConverters::class)
internal abstract class LedgerDatabase : RoomDatabase() {
    abstract val dao: LedgerDao

    companion object {

        /**
         * Moves the single scalar `sharedId` column onto its own one-to-many
         * table, so an event can belong to several shared groups at once.
         *
         * Unlike a Core Data lightweight migration, a plain SQL rebuild has no
         * mapping limitation that would force a device's pooled-group
         * association to reset: every non-null `sharedId` carries forward as
         * that event's one row in [LedgerSharedIdEntity], alongside its
         * unchanged per-schedule history.
         *
         * `ledger_events` is rebuilt (copy, drop, rename) rather than altered
         * in place, since dropping a column is gated on the SQLite version
         * bundled with the device's OS. The old column's values are staged in
         * a temp table first, and `ledger_shared_ids` (with its foreign key to
         * `ledger_events`) is only created once the rebuilt table has taken
         * its final name — SQLite only reliably rewrites a foreign key's
         * table reference on rename from 3.25.3 onward, and Android's bundled
         * SQLite version varies by OS level, so this ordering avoids the
         * question entirely rather than relying on that fix being present.
         */
        internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TEMP TABLE ledger_events_legacy_shared_ids AS " +
                        "SELECT id AS eventId, sharedId AS value FROM ledger_events " +
                        "WHERE sharedId IS NOT NULL"
                )

                db.execSQL(
                    "CREATE TABLE ledger_events_new (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`scheduleId` TEXT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "`body` TEXT NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO ledger_events_new (id, scheduleId, timestamp, body) " +
                        "SELECT id, scheduleId, timestamp, body FROM ledger_events"
                )
                // The rebuild copies ids explicitly, so every event keeps the id
                // the staged `ledger_shared_ids` rows reference. It does not
                // carry the AUTOINCREMENT high-water mark across, though:
                // `DROP TABLE` takes the old table's `sqlite_sequence` row with
                // it, leaving the counter at the largest surviving id rather
                // than the largest ever issued. Deliberately not repaired —
                // nothing reads an event id outside this table, so no row can
                // collide, and writing to `sqlite_sequence` by hand inside a
                // migration is a real risk taken for no behavioural gain. See
                // `LedgerEventEntity`, which says what this does and does not
                // guarantee.
                db.execSQL("DROP TABLE ledger_events")
                db.execSQL("ALTER TABLE ledger_events_new RENAME TO ledger_events")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ledger_events_scheduleId` " +
                        "ON `ledger_events` (`scheduleId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ledger_events_timestamp` " +
                        "ON `ledger_events` (`timestamp`)"
                )

                db.execSQL(
                    "CREATE TABLE `ledger_shared_ids` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`eventId` INTEGER NOT NULL, " +
                        "`value` TEXT NOT NULL, " +
                        "FOREIGN KEY(`eventId`) REFERENCES `ledger_events`(`id`) " +
                        "ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ledger_shared_ids_value` " +
                        "ON `ledger_shared_ids` (`value`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ledger_shared_ids_eventId` " +
                        "ON `ledger_shared_ids` (`eventId`)"
                )

                db.execSQL(
                    "INSERT INTO ledger_shared_ids (eventId, value) " +
                        "SELECT eventId, value FROM ledger_events_legacy_shared_ids"
                )
                db.execSQL("DROP TABLE ledger_events_legacy_shared_ids")
            }
        }

        fun createDatabase(context: Context, config: AirshipRuntimeConfig): LedgerDatabase {
            val name = config.configOptions.appKey + "_ledger"
            val path = File(ContextCompat.getNoBackupFilesDir(context), name).absolutePath
            return databaseBuilder(
                context,
                LedgerDatabase::class.java,
                path
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()
        }

        @VisibleForTesting
        internal fun createInMemoryDatabase(context: Context): LedgerDatabase =
            Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}

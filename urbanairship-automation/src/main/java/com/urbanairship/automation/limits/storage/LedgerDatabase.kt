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
import androidx.sqlite.db.SupportSQLiteDatabase
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.db.RecoverableMigration
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
         * Every non-null, non-empty `sharedId` carries forward as that event's
         * one row in [LedgerSharedIdEntity].
         *
         * `ledger_events` is rebuilt (copy, drop, rename) rather than altered
         * in place: dropping a column is gated on the device's bundled SQLite
         * version. Row ids are copied explicitly, because the staged
         * shared-group rows reference them.
         *
         * Order matters. `ledger_shared_ids` is created only after the rebuilt
         * table has taken its final name, since SQLite only reliably rewrites a
         * foreign key's table reference on rename from 3.25.3 onward.
         *
         * Recovers by dropping to an empty v2 schema. Losing the ledger costs a
         * device its display history — every pooled limit restarts from zero —
         * but a throw here is an unhandled crash on database open.
         */
        internal val MIGRATION_1_2: RecoverableMigration = object : RecoverableMigration(1, 2) {
            override fun tryMigrate(db: SupportSQLiteDatabase) {
                // Empty strings are excluded alongside NULL: `LedgerSharedIds`
                // drops them when canonicalizing, so a promoted `''` row would
                // never match the schedule config it came from.
                db.execSQL(
                    "CREATE TEMP TABLE ledger_events_legacy_shared_ids AS " +
                        "SELECT id AS eventId, sharedId AS value FROM ledger_events " +
                        "WHERE sharedId IS NOT NULL AND sharedId != ''"
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
                db.execSQL("DROP TABLE ledger_events")
                db.execSQL("ALTER TABLE ledger_events_new RENAME TO ledger_events")
                createV2Schema(db)

                db.execSQL(
                    "INSERT INTO ledger_shared_ids (eventId, value) " +
                        "SELECT eventId, value FROM ledger_events_legacy_shared_ids"
                )
                db.execSQL("DROP TABLE ledger_events_legacy_shared_ids")
            }

            override fun tryRecover(db: SupportSQLiteDatabase, err: Exception) {
                db.execSQL("DROP TABLE IF EXISTS ledger_events_legacy_shared_ids")
                db.execSQL("DROP TABLE IF EXISTS ledger_shared_ids")
                db.execSQL("DROP TABLE IF EXISTS ledger_events_new")
                db.execSQL("DROP TABLE IF EXISTS ledger_events")
                db.execSQL(
                    "CREATE TABLE ledger_events (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`scheduleId` TEXT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "`body` TEXT NOT NULL)"
                )
                createV2Schema(db)
            }

            /** The v2 indices and the shared-group table, on an existing `ledger_events`. */
            private fun createV2Schema(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ledger_events_scheduleId` " +
                        "ON `ledger_events` (`scheduleId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ledger_events_timestamp` " +
                        "ON `ledger_events` (`timestamp`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ledger_shared_ids` (" +
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
                .fallbackToDestructiveMigration(true)
                .build()
        }

        @VisibleForTesting
        internal fun createInMemoryDatabase(context: Context): LedgerDatabase =
            Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}

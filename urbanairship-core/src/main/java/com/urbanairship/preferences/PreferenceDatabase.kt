/* Copyright Airship and Contributors */
package com.urbanairship.preferences

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.room.Database
import androidx.room.Room.databaseBuilder
import androidx.room.Room.inMemoryDatabaseBuilder
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.urbanairship.AirshipConfigOptions
import java.io.File

/**
 * Preferences database.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Database(entities = [PreferenceData::class], version = 3)
public abstract class PreferenceDatabase public constructor() : RoomDatabase() {

    public abstract val dao: PreferenceDao

    public fun exists(context: Context): Boolean {
        return openHelper.databaseName == null
                || context.getDatabasePath(openHelper.databaseName).exists()
    }

    public companion object {

        private const val DATABASE_DIRECTORY_NAME = "com.urbanairship.databases"
        public const val DATABASE_NAME: String = "ua_preferences.db"
        public const val TABLE_NAME: String = "preferences"
        public const val NEW_TABLE_NAME: String = "preferences_new"
        public const val COLUMN_NAME_KEY: String = "_id"
        public const val COLUMN_NAME_VALUE: String = "value"
        public const val COLUMN_NAME_LAZY: String = "lazy"

        /**
         * Adds the `lazy` column and drops the v3 obsolete-keys batch in one shot.
         *
         * Pattern for retiring more keys later: don't edit [OBSOLETE_KEYS_V3] — users already on
         * v3 won't re-run this migration. Instead, add an `OBSOLETE_KEYS_V4` array next to a new
         * `MIGRATION_3_4` and bump the [Database] version.
         */
        internal val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_NAME_LAZY INTEGER NOT NULL DEFAULT 0")
                val placeholders = OBSOLETE_KEYS_V3.joinToString(",") { "?" }
                db.execSQL(
                    "DELETE FROM $TABLE_NAME WHERE $COLUMN_NAME_KEY IN ($placeholders)",
                    OBSOLETE_KEYS_V3
                )
            }
        }

        /** Keys retired by v3; dropped by [MIGRATION_2_3]. */
        private val OBSOLETE_KEYS_V3 = arrayOf<Any>(
            "com.urbanairship.TAG_GROUP_HISTORIAN_RECORDS",
            "com.urbanairship.push.iam.PENDING_IN_APP_MESSAGE",
            "com.urbanairship.push.iam.AUTO_DISPLAY_ENABLED",
            "com.urbanairship.push.iam.LAST_DISPLAYED_ID",
            "com.urbanairship.nameduser.CHANGE_TOKEN_KEY",
            "com.urbanairship.nameduser.LAST_UPDATED_TOKEN_KEY",
            "com.urbanairship.iam.tags.TAG_PREFER_LOCAL_DATA_TIME",
            "com.urbanairship.chat.CHAT",
            "com.urbanairship.user.LAST_MESSAGE_REFRESH_TIME",
            "com.urbanairship.push.LAST_REGISTRATION_TIME",
            "com.urbanairship.push.LAST_REGISTRATION_PAYLOAD",
            "com.urbanairship.remotedata.LAST_REFRESH_APP_VERSION",
            "com.urbanairship.remotedata.LAST_MODIFIED",
            "com.urbanairship.remotedata.LAST_REFRESH_TIME",
            "com.urbanairship.iam.data.last_payload_info",
            "com.urbanairship.iam.data.LAST_PAYLOAD_METADATA",
            "com.urbanairship.iam.data.contact_last_payload_info",
            "com.urbanairship.push.SOUND_ENABLED",
            "com.urbanairship.push.VIBRATE_ENABLED",
            "com.urbanairship.push.QUIET_TIME_ENABLED",
            "com.urbanairship.push.QUIET_TIME_INTERVAL"
        )

        private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                //Create new table
                db.execSQL(
                    ("CREATE TABLE $NEW_TABLE_NAME ($COLUMN_NAME_KEY TEXT PRIMARY KEY NOT NULL, $COLUMN_NAME_VALUE TEXT);")
                )

                //Copy the data
                db.execSQL(
                    ("INSERT INTO $NEW_TABLE_NAME ($COLUMN_NAME_KEY, $COLUMN_NAME_VALUE) SELECT $COLUMN_NAME_KEY, $COLUMN_NAME_VALUE FROM $TABLE_NAME")
                )

                //Remove the old table
                db.execSQL("DROP TABLE $TABLE_NAME")

                //Rename the new table
                db.execSQL("ALTER TABLE $NEW_TABLE_NAME RENAME TO $TABLE_NAME")
            }
        }

        public fun createDatabase(
            context: Context,
            config: AirshipConfigOptions
        ): PreferenceDatabase {
            val name = config.appKey + "_" + DATABASE_NAME
            val urbanAirshipNoBackupDirectory = File(ContextCompat.getNoBackupFilesDir(context), DATABASE_DIRECTORY_NAME)
            val path = File(urbanAirshipNoBackupDirectory, name).absolutePath

            return databaseBuilder(context, PreferenceDatabase::class.java, path)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()
        }

        @VisibleForTesting
        public fun createInMemoryDatabase(context: Context): PreferenceDatabase {
            return inMemoryDatabaseBuilder(context, PreferenceDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        }
    }
}
